package votbot.event

import votbot.Main.VotbotEnv
import votbot.model.Irc
import votbot.model.Irc.{ Command, Prefix, RawMessage }
import votbot.{ Api, BotState }
import zio.ZIO
import zio.console.putStrLn

object Event {
  sealed trait Event
  trait IncomingMessage extends Event {val sender: String; val msg: String }

  final case class ChannelMessage(sender: String, channel: String, msg: String)             extends IncomingMessage
  final case class PrivateMessage(sender: String, msg: String)                              extends IncomingMessage
  final case class Join(user: String, channel: String)                                      extends Event
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
  final case class Unknown(raw: RawMessage)                                                 extends Event

  final case object Connected extends Event

  def ircToEvent(ircMsg: RawMessage): ZIO[BotState, Throwable, Event] =
    for {
      state <- ZIO.accessM[BotState](_.state.get)
      event = ircMsg match {
        case RawMessage(Command.Ping, args, _) =>
          Ping(args.headOption)
        case RawMessage(Command.Privmsg, args, Some(prefix)) =>
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
        case RawMessage(Command.Numeric(Irc.Numeric.RPL_WELCOME), args, Some(prefix)) if args.nonEmpty =>
          Welcome(args.head, prefix.host)
        case RawMessage(Command.Numeric(cmd), args, Some(prefix)) =>
          Numeric(cmd, args, prefix)
        case RawMessage(Command.Join, channel, Some(prefix)) if prefix.nick.equalsIgnoreCase(state.nick) =>
          BotJoin(channel.mkString(", "))
        case RawMessage(Command.Part, channel, Some(prefix)) if prefix.nick.equalsIgnoreCase(state.nick) =>
          BotPart(channel.mkString(", "))
        case RawMessage(Command.Join, args, Some(prefix)) =>
          Join(prefix.nick, args.mkString(", "))
        case RawMessage(Command.Part, args, Some(prefix)) =>
          Part(prefix.nick, args.dropRight(1).mkString(", "), args.last) //last - reason
        case RawMessage(Command.Notice, args, Some(prefix)) =>
          if (args.head.startsWith("#"))
            ChannelNotice(prefix.nick, args.head, args.last)
          else
            Notice(prefix.nick, args.last)
        case RawMessage(Command.Quit, args, Some(prefix)) =>
          Quit(prefix.nick, args.mkString)
        case _ => Unknown(ircMsg)
      }
    } yield event

  def eventProcessor(): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      api     <- ZIO.environment[Api]
      evt     <- api.dequeueEvent()
      handler <- ZIO.environment[BaseEventHandler]
      _       <- putStrLn("Processing Event: " + evt.toString)
      _       <- handler.handle(evt)
    } yield ()

}
