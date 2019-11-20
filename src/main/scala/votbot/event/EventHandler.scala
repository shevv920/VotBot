package votbot.event

import votbot.Main.VotbotEnv
import votbot.event.Event.{ BotJoin, BotPart, Connected, Event, Join, Numeric, Part, Ping, Quit, Welcome }
import votbot.model.Irc.Numeric.{ ERR_NICKNAMEINUSE, RPL_ENDOFNAMES, RPL_NAMREPLY, RPL_YOURHOST }
import votbot.model.Irc._
import votbot.{ Api, BotState, Configuration }
import zio.random.Random
import zio.{ Ref, ZIO }

trait EventHandler {
  def handle(event: Event): ZIO[VotbotEnv, Throwable, Unit]
}

trait BaseEventHandler extends EventHandler {
  val customHandlers: Ref[List[EventHandler]]

  def addCustom(handler: EventHandler): ZIO[VotbotEnv, Throwable, Unit] =
    customHandlers
      .update(hs => handler :: hs)
      .unit

  def removeCustom(handler: EventHandler): ZIO[VotbotEnv, Throwable, Unit] =
    customHandlers
      .update(_.filterNot(h => handler == h))
      .unit

  override def handle(event: Event): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      cfg   <- ZIO.environment[Configuration].map(_.config)
      api   <- ZIO.environment[Api]
      state <- ZIO.access[BotState](_.state)
      _ <- ZIO.whenCase(event) {
            case Ping(Some(args)) =>
              api.enqueueOutMessage(RawMessage(Command.Pong, args))
            case Ping(None) =>
              api.enqueueOutMessage(RawMessage(Command.Pong))
            case Numeric(RPL_YOURHOST, _, prefix) =>
              ZIO.unit
            case Numeric(ERR_NICKNAMEINUSE, _, prefix) =>
              for {
                n       <- ZIO.access[Random](_.random.nextInt(99))
                newNick = cfg.bot.nick + n
                _       <- api.enqueueOutMessage(RawMessage(Command.Nick, newNick))
                _       <- state.update(s => s.copy(nick = newNick))
              } yield ()
            case Connected =>
              val nickCmd = RawMessage(Command.Nick, cfg.bot.nick)
              val userCmd = RawMessage(Command.User, cfg.bot.userName, "*", "*", cfg.bot.realName)
              api.enqueueOutMessage(nickCmd, userCmd)
            case Welcome(nick, host) =>
              api
                .enqueueOutMessage(RawMessage(Command.Join, cfg.bot.autoJoinChannels.mkString(",")))
                .flatMap(_ => state.update(_.copy(nick = nick)))
            case BotJoin(chName) =>
              api.addChannel(Channel(chName, List.empty, Set.empty))
            case BotPart(channel) =>
              api.removeChannel(channel)
            case Join(user, channel) =>
              api.addChannelMember(channel, ChannelMember(user, List.empty))
            case Part(user, channel, reason) =>
              api.removeChannelMember(channel, user)
            case Numeric(RPL_NAMREPLY, args, prefix) if args.size > 2 =>
              val names = args.last.split(" ").map {
                case n if n.startsWith("@") =>
                  ChannelMember(n.drop(1), List(ChannelMode("o", Some(n.drop(1)))))
                case n if n.startsWith("+") =>
                  ChannelMember(n.drop(1), List(ChannelMode("v", Some(n.drop(1)))))
                case n =>
                  ChannelMember(n, List.empty)
              }
              api.addChannelMember(args(2), names: _*)
            case Numeric(RPL_ENDOFNAMES, args, prefix) =>
              ZIO.unit
            case Quit(user, reason) =>
              ???
          }
      handlers <- customHandlers.get
      _        <- ZIO.foreach(handlers)(handler => handler.handle(event))
    } yield ()
}
