package votbot.event

import votbot.Main.VotbotEnv
import votbot.event.handlers.BaseEventHandler
import votbot.model.irc._
import votbot.{ Api, BotState }
import zio.ZIO
import zio.console.putStrLn
import zio.nio.SocketAddress

object Event {
  sealed trait Event
  trait IncomingMessage extends Event { val sender: String; val msg: String }

  final case class ChannelMessage(sender: String, channel: String, msg: String)             extends IncomingMessage
  final case class PrivateMessage(sender: String, msg: String)                              extends IncomingMessage
  final case class Join(user: String, channel: String)                                      extends Event
  final case class ExtendedJoin(user: String, channel: String, accountName: String)         extends Event
  final case class BotJoin(channel: String)                                                 extends Event
  final case class Part(user: String, channel: String, reason: String)                      extends Event
  final case class BotPart(channel: String)                                                 extends Event
  final case class Notice(sender: String, msg: String)                                      extends Event
  final case class ChannelNotice(sender: String, channel: String, msg: String)              extends Event
  final case class ChannelCTCP(nick: String, channel: String, special: String, msg: String) extends Event
  final case class PrivateCTCP(nick: String, special: String, msg: String)                  extends Event
  final case class Ping(args: Option[String])                                               extends Event
  final case class Pong(args: String)                                                       extends Event
  final case class Welcome(nick: String, host: String)                                      extends Event
  final case class Numeric(cmd: String, msg: Vector[String], prefix: Prefix)                extends Event
  final case class Quit(user: String, reason: String)                                       extends Event
  final case class NamesList(channel: String, members: List[(String, List[ChannelMode])])   extends Event
  final case class CapabilityList(caps: List[String])                                       extends Event
  final case class CapabilityAck(caps: List[String])                                        extends Event
  final case class CapabilityNak(caps: List[String])                                        extends Event
  final case class UserLoggedIn(nick: String, accountName: String)                          extends Event
  final case class UserLoggedOut(nick: String)                                              extends Event
  final case class NickChanged(oldNick: String, newNick: String)                            extends Event
  final case class Unknown(raw: Message)                                                    extends Event
  final case class Connected(remote: SocketAddress)                                         extends Event

  def ircToEvent(ircMsg: Message): ZIO[BotState, Throwable, Event] =
    for {
      state          <- ZIO.access[BotState](_.botState)
      currentNick    <- state.currentNick()
      isExtendedJoin <- state.isCapabilityEnabled(Capabilities.ExtendedJoin)
      event = ircMsg match {
        case Message(Command.Ping, args, _) =>
          Ping(args.headOption)
        case Message(Command.Privmsg, args, Some(prefix)) if !prefix.nick.equalsIgnoreCase(currentNick) =>
          if (args.last.startsWith("\u0001")) {
            val special = args.last.drop(1).takeWhile(_ != ' ')
            val msg     = args.last.drop(1 + special.length)
            if (args.head.startsWith("#"))
              ChannelCTCP(prefix.nick, args.head, special, msg)
            else
              PrivateCTCP(prefix.nick, special, msg)
          } else {
            if (args.head.startsWith("#"))
              ChannelMessage(prefix.nick, args.head, args.last)
            else
              PrivateMessage(prefix.nick, args.last)
          }
        case Message(Command.Numeric(NumericCommand.RPL_WELCOME), args, Some(prefix)) if args.nonEmpty =>
          Welcome(args.head, prefix.host)
        case Message(Command.Numeric(NumericCommand.RPL_NAMREPLY), args, _) =>
          val channel = args(2)
          val members = args.last
            .split(" ")
            .map {
              case n if n.startsWith("@") =>
                (n.drop(1), List(ChannelMode("o", Some(n.drop(1)))))
              case n if n.startsWith("+") =>
                (n.drop(1), List(ChannelMode("v", Some(n.drop(1)))))
              case n =>
                (n, List.empty)
            }
            .toList
          NamesList(channel, members)
        case rm @ Message(Command.Join, _, _) =>
          parseJoin(currentNick, isExtendedJoin, rm)
        case Message(Command.Part, channel, Some(prefix)) if prefix.nick.equalsIgnoreCase(currentNick) =>
          BotPart(channel.mkString(", "))
        case Message(Command.Part, args, Some(prefix)) if args.size > 1 =>
          Part(prefix.nick, args.dropRight(1).mkString(", "), args.last) //last - reason
        case Message(Command.Part, args, Some(prefix)) =>
          Part(prefix.nick, args.mkString(", "), prefix.nick)
        case Message(Command.Notice, args, Some(prefix)) =>
          if (args.head.startsWith("#"))
            ChannelNotice(prefix.nick, args.head, args.last)
          else
            Notice(prefix.nick, args.last)
        case Message(Command.Quit, args, Some(prefix)) =>
          Quit(prefix.nick, args.mkString)
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
              UserLoggedOut(prefix.nick)
            case accName =>
              UserLoggedIn(prefix.nick, accName)
          }
        case Message(Command.Nick, args, Some(prefix)) if args.nonEmpty =>
          NickChanged(prefix.nick, args.head)
        case Message(Command.Numeric(NumericCommand.RPL_WHOREPLYX), args, Some(prefix)) =>
          args match {
            case Vector(_, targetNick, targetAcc) if targetAcc != "0" =>
              UserLoggedIn(targetNick, targetAcc)
            case _ => Unknown(ircMsg)
          }
        case Message(Command.Numeric(cmd), args, Some(prefix)) =>
          Numeric(cmd, args, prefix)
        case _ => Unknown(ircMsg)
      }
    } yield event

  private def parseJoin(botNick: String, isExtended: Boolean, message: Message): Event =
    message match {
      case Message(Command.Join, args, Some(prefix)) if prefix.nick.equalsIgnoreCase(botNick) =>
        BotJoin(args.head)
      case Message(Command.Join, args, Some(prefix)) if !isExtended =>
        Join(prefix.nick, args.head)
      case Message(Command.Join, args, Some(prefix)) if args.size > 1 =>
        val channel     = args.head
        val accountName = args.tail.head
        ExtendedJoin(prefix.nick, channel, accountName)
    }

  def eventProcessor(): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      api     <- ZIO.access[Api](_.api)
      evt     <- api.dequeueEvent()
      handler <- ZIO.access[BaseEventHandler](_.baseEventHandler)
      _       <- putStrLn("Processing Event: " + evt.toString)
      _       <- handler.handle(evt)
    } yield ()

}
