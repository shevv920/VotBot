package votbot.event.handlers
import votbot.event.Event._
import votbot.event.EventHandler
import votbot.model.Irc.Numeric.{ERR_NICKNAMEINUSE, RPL_ENDOFNAMES, RPL_YOURHOST}
import votbot.model.Irc.{Channel, Command, RawMessage}
import votbot.{Api, BotState, Configuration}
import zio.random.Random
import zio.{Ref, ZIO}

trait BaseEventHandler extends EventHandler {
  val customHandlers: Ref[List[EventHandler]]

  def addCustom(handler: EventHandler): ZIO[Any, Throwable, Unit] =
    customHandlers
      .update(hs => handler :: hs)
      .unit

  def removeCustom(handler: EventHandler): ZIO[Any, Throwable, Unit] =
    customHandlers
      .update(_.filterNot(h => handler == h))
      .unit

  override def handle(event: Event): ZIO[HandlerEnv, Throwable, Unit] =
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
            case Join(name, channel) =>
              for {
                user <- api.getOrCreateUser(name)
                ch   <- api.getChannel(channel)
                _    <- api.addChannelMember(ch, user)
                _    <- api.addChannelToUser(ch, user)
              } yield ()
            case Part(user, channel, reason) =>
              api.removeChannelMember(channel, user)
            case NamesList(chName, members) =>
              for {
                channelMembers <- ZIO.foreach(members) { tuple =>
                                   for {
                                     user  <- api.getOrCreateUser(tuple._1)
                                     modes = tuple._2
                                   } yield user
                                 }
                _ <- ZIO.foreach(channelMembers)(api.addChannelMember(chName, _))
              } yield ()
            case Numeric(RPL_ENDOFNAMES, args, prefix) =>
              ZIO.unit
            case Quit(user, reason) =>
              for {
                user <- api.getUser(user)
                _    <- api.removeUser(user)
              } yield ()
          }
      handlers <- customHandlers.get
      _        <- ZIO.foreach(handlers)(handler => handler.handle(event))
    } yield ()
}
