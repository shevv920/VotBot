package votbot.event

import votbot.BotState
import votbot.Main.VotbotEnv
import votbot.model.irc._
import zio.ZIO
import zio.nio.core.SocketAddress

sealed trait Event extends Serializable with Product

object Event {
  type HandleFunction = PartialFunction[Event, ZIO[VotbotEnv, Throwable, Unit]]
  val emptyHandleFunction: HandleFunction = { case _ => ZIO.unit }
  final case class Handler(name: String, handleFunction: HandleFunction)

  final case class IncomingMessage(sender: UserKey, target: Either[ChannelKey, UserKey], msg: String) extends Event

  final case class Join(user: UserKey, channel: ChannelKey)                                      extends Event
  final case class ExtendedJoin(user: UserKey, channel: ChannelKey, accountName: String)         extends Event
  final case class BotJoin(channel: String)                                                      extends Event
  final case class Part(userKey: UserKey, channel: ChannelKey, reason: String)                   extends Event
  final case class BotPart(channel: ChannelKey)                                                  extends Event
  final case class Notice(sender: UserKey, msg: String)                                          extends Event
  final case class ChannelNotice(sender: UserKey, channel: ChannelKey, msg: String)              extends Event
  final case class ChannelCTCP(user: UserKey, channel: ChannelKey, special: String, msg: String) extends Event
  final case class PrivateCTCP(user: UserKey, special: String, msg: String)                      extends Event
  final case class Ping(args: Option[String])                                                    extends Event
  final case class Pong(args: String)                                                            extends Event
  final case class Welcome(nick: String, host: String)                                           extends Event
  final case class Numeric(cmd: String, args: Vector[String], prefix: Prefix)                    extends Event
  final case class Quit(user: UserKey, reason: String)                                           extends Event
  final case class NamesList(channel: ChannelKey, members: List[(String, List[ChannelMode])])    extends Event
  final case class CapabilityList(caps: List[String])                                            extends Event
  final case class CapabilityAck(caps: List[String])                                             extends Event
  final case class CapabilityNak(caps: List[String])                                             extends Event
  final case class UserLoggedIn(nick: UserKey, accountName: String)                              extends Event
  final case class UserLoggedOut(nick: UserKey)                                                  extends Event
  final case class NickChanged(oldNick: String, newNick: String)                                 extends Event
  final case class Unknown(raw: Message)                                                         extends Event
  final case class Connected(remote: SocketAddress)                                              extends Event
  final case class CommandTriggered(cmd: votbot.command.Command, userKey: UserKey)               extends Event

  def fromIrcMessage(ircMsg: Message): ZIO[BotState, Throwable, Event] =
    for {
      state          <- ZIO.access[BotState](_.botState)
      currentNick    <- state.currentNick()
      isExtendedJoin <- state.isCapabilityEnabled(Capabilities.ExtendedJoin)
      event = ircMsg match {
        case Message(Command.Ping, args, _) =>
          Ping(args.headOption)
        case Message(Command.Privmsg, target :: last :: Nil, Some(prefix))
            if !prefix.nick.equalsIgnoreCase(currentNick) =>
          if (last.startsWith("\u0001")) {
            val special = last.drop(1).takeWhile(_ != ' ')
            val msg     = last.drop(1 + special.length)
            if (target.startsWith("#"))
              ChannelCTCP(UserKey(prefix.nick), ChannelKey(target), special, msg)
            else
              PrivateCTCP(UserKey(prefix.nick), special, msg)
          } else {
            if (target.startsWith("#"))
              IncomingMessage(UserKey(prefix.nick), Left(ChannelKey(target)), last)
            else
              IncomingMessage(UserKey(prefix.nick), Right(UserKey(target)), last)
          }
        case Message(Command.Numeric(NumericCommand.RPL_WELCOME), args, Some(prefix)) if args.nonEmpty =>
          Welcome(args.head, prefix.host)
        case Message(Command.Numeric(NumericCommand.RPL_NAMREPLY), args, _) if args.size >= 3 =>
          val channel = args(2)
          val members = args.last
            .split(" ")
            .map {
              case n if n.startsWith("@") =>
                (n.drop(1), List(ChannelMode.o(UserKey(n.drop(1)))))
              case n if n.startsWith("+") =>
                (n.drop(1), List(ChannelMode.v(UserKey(n.drop(1)))))
              case n =>
                (n, List.empty)
            }
            .toList
          NamesList(ChannelKey(channel), members)
        case rm @ Message(Command.Join, _, _) =>
          parseJoin(currentNick, isExtendedJoin, rm)
        case Message(Command.Part, channels, Some(prefix)) if prefix.nick.equalsIgnoreCase(currentNick) =>
          /*
                    Servers MUST be able to parse arguments in the form of a list of
                    target, but SHOULD NOT use lists when sending PART messages to
                    clients.
                    IRC RFC 2812 - https://tools.ietf.org/html/rfc2812#section-3.2.2
           */
          BotPart(ChannelKey(channels.mkString("")))
        case Message(Command.Part, args, Some(prefix)) if args.size > 1 =>
          Part(UserKey(prefix.nick), ChannelKey(args.dropRight(1).mkString(", ")), args.last) //last - reason
        case Message(Command.Part, args, Some(prefix)) =>
          Part(UserKey(prefix.nick), ChannelKey(args.mkString), prefix.nick)
        case Message(Command.Notice, target :: msg :: Nil, Some(prefix)) =>
          if (target.startsWith("#"))
            ChannelNotice(UserKey(prefix.nick), ChannelKey(target), msg)
          else
            Notice(UserKey(prefix.nick), msg)
        case Message(Command.Quit, args, Some(prefix)) =>
          Quit(UserKey(prefix.nick), args.mkString)
        case Message(Command.Cap, args, _) if args.size == 3 =>
          val subCmd = args(1)
          val caps   = args(2).split(" ").toList
          subCmd.toUpperCase match {
            case "LS" | "LIST" =>
              CapabilityList(caps)
            case "ACK" =>
              CapabilityAck(caps)
            case "NAK" =>
              CapabilityNak(caps)
          }
        case Message(Command.Account, args, Some(prefix)) if args.nonEmpty =>
          args.head match {
            case "*" =>
              UserLoggedOut(UserKey(prefix.nick))
            case accName =>
              UserLoggedIn(UserKey(prefix.nick), accName)
          }
        case Message(Command.Nick, args, Some(prefix)) if args.nonEmpty =>
          NickChanged(prefix.nick, args.head)
        case Message(Command.Numeric(NumericCommand.RPL_WHOREPLYX), args, Some(prefix)) =>
          args match {
            case _ :: targetNick :: targetAcc :: Nil if targetAcc != "0" =>
              UserLoggedIn(UserKey(targetNick), targetAcc)
            case _ => Unknown(ircMsg)
          }
        case Message(Command.Numeric(cmd), args, Some(prefix)) =>
          Numeric(cmd, args.toVector, prefix)
        case _ => Unknown(ircMsg)
      }
    } yield event

  private def parseJoin(botNick: String, isExtended: Boolean, message: Message): Event =
    message match {
      case Message(Command.Join, args, Some(prefix)) if prefix.nick.equalsIgnoreCase(botNick) =>
        BotJoin(args.head)
      case Message(Command.Join, args, Some(prefix)) if !isExtended =>
        Join(UserKey(prefix.nick), ChannelKey(args.head))
      case Message(Command.Join, args, Some(prefix)) if args.size > 1 =>
        val channel     = args.head
        val accountName = args.tail.head
        ExtendedJoin(UserKey(prefix.nick), ChannelKey(channel), accountName)
    }

}
