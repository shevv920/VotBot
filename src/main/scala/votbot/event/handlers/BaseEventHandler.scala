package votbot.event.handlers
import votbot.event.Event._
import votbot.event.EventHandler
import votbot.model.irc._
import votbot.{ Api, BotState, Configuration }
import zio.random.Random
import zio.{ Ref, ZIO }

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
      api   <- ZIO.access[Api](_.api)
      state <- ZIO.access[BotState](_.state)
      _ <- ZIO.whenCase(event) {
            case Ping(Some(args)) =>
              api.enqueueOutMessage(RawMessage(Command.Pong, args))
            case Ping(None) =>
              api.enqueueOutMessage(RawMessage(Command.Pong))
            case Numeric(NumericCommand.RPL_YOURHOST, _, prefix) =>
              ZIO.unit
            case Numeric(NumericCommand.ERR_NICKNAMEINUSE, _, prefix) =>
              for {
                n       <- ZIO.access[Random](_.random.nextInt(99))
                newNick = cfg.bot.nick + n
                _       <- api.enqueueOutMessage(RawMessage(Command.Nick, newNick))
                _       <- state.update(s => s.copy(nick = newNick))
              } yield ()
            case Connected =>
              val capLsCmd = RawMessage(Command.CapLs)
              val nickCmd  = RawMessage(Command.Nick, cfg.bot.nick)
              val userCmd  = RawMessage(Command.User, cfg.bot.userName, "*", "*", cfg.bot.realName)
              api.enqueueOutMessage(capLsCmd, nickCmd, userCmd)
            case CapabilityList(supportedCaps) =>
              val capsFromCfg          = cfg.server.capRequire.getOrElse(List.empty).map(_.toLowerCase)
              val supportedAndRequired = capsFromCfg.intersect(supportedCaps.map(_.toLowerCase))
              val capReqCmd =
                RawMessage(Command.CapReq, ":" + supportedAndRequired.mkString(" "))
              val capEnd = RawMessage(Command.CapEnd)
              api.enqueueOutMessage(capReqCmd, capEnd)
            case CapabilityAck(caps) =>
              val capsSupported = caps
                .map(Capabilities.withNameInsensitiveOption)
                .filter(_.nonEmpty)
                .map(_.get)
              state.update(s => s.copy(capabilities = s.capabilities ++ capsSupported))
            case Welcome(nick, host) =>
              val joinCmd = RawMessage(Command.Join, cfg.bot.autoJoinChannels.mkString(","))
              api
                .enqueueOutMessage(joinCmd)
                .flatMap(_ => state.update(_.copy(nick = nick)))
            case BotJoin(chName) =>
              api.addChannel(Channel(chName, List.empty, Set.empty))
            case BotPart(channel) =>
              api.removeChannel(ChannelKey(channel))
            case Join(name, channel) =>
              for {
                user  <- api.getOrCreateUser(name)
                chKey = ChannelKey(channel)
                _     <- api.addChannelMember(chKey, user)
                _     <- api.addChannelToUser(chKey, UserKey(user.name))
              } yield ()
            case Part(user, channel, reason) =>
              api.removeChannelMember(ChannelKey(channel), UserKey(user))
            case NamesList(chName, members) =>
              for {
                channelMembers <- ZIO.foreach(members) { tuple =>
                                   for {
                                     user  <- api.getOrCreateUser(tuple._1)
                                     modes = tuple._2
                                   } yield user
                                 }
                _ <- ZIO.foreach(channelMembers)(api.addChannelMember(ChannelKey(chName), _))
              } yield ()
            case Numeric(NumericCommand.RPL_ENDOFNAMES, args, prefix) =>
              ZIO.unit
            case Quit(userName, reason) =>
              for {
                user <- api.getUser(UserKey(userName))
                _    <- api.removeUser(user)
              } yield ()
          }
      handlers <- customHandlers.get
      _        <- ZIO.foreach(handlers)(handler => handler.handle(event))
    } yield ()
}
