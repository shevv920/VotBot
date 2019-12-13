package votbot.event.handlers
import votbot.event.Event._
import votbot.event.EventHandler
import votbot.model.irc._
import votbot.{ Api, BotState, Configuration }
import zio.random.Random
import zio.{ Ref, ZIO }
import zio.console.putStrLn

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
                n       <- ZIO.accessM[Random](_.random.nextInt(99))
                newNick = cfg.bot.nick + n
                _       <- api.enqueueOutMessage(RawMessage(Command.Nick, newNick))
                _       <- state.setNick(newNick)
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
              state.addCapabilities(capsSupported: _*)
            case CapabilityNak(caps) =>
              val capsToRemove = caps
                .map(Capabilities.withNameInsensitiveOption)
                .filter(_.nonEmpty)
                .map(_.get)
              state.removeCapabilities(capsToRemove: _*)
            case Welcome(nick, host) =>
              val joinCmd = RawMessage(Command.Join, cfg.bot.autoJoinChannels.mkString(","))
              api
                .enqueueOutMessage(joinCmd)
                .flatMap(_ => state.setNick(nick))
            case BotJoin(chName) =>
              api.addChannel(Channel(chName, List.empty, Set.empty))
            case BotPart(channel) =>
              api.removeChannel(ChannelKey(channel))
            case Join(name, channel) =>
              for {
                user  <- api.getOrCreateUser(name)
                chKey = ChannelKey(channel)
                _     <- api.addUserToChannel(chKey, user)
                _     <- api.addChannelToUser(chKey, UserKey(user.name))
              } yield ()
            case ExtendedJoin(name, channel, accountName) =>
              for {
                _ <- api.enqueueEvent(Join(name, channel))
                _ <- ZIO.whenCase(accountName) {
                      case "*" =>
                        ZIO.unit
                      case accName =>
                        api.enqueueEvent(UserLoggedIn(name, accName))
                    }
              } yield ()
            case Part(user, channel, reason) =>
              api.removeChannelMember(ChannelKey(channel), UserKey(user))
            case NamesList(chName, members) =>
              for {
                currentNick <- state.currentNick()
                //filter out bot from names list
                channelMembers <- ZIO.foreach(members.filterNot(_._1.equalsIgnoreCase(currentNick))) { tuple =>
                                   for {
                                     user  <- api.getOrCreateUser(tuple._1)
                                     modes = tuple._2
                                   } yield user
                                 }
                _ <- ZIO.foreach(channelMembers)(api.addUserToChannel(ChannelKey(chName), _))
                _ <- ZIO.foreach(channelMembers)(u => api.askForAccByName(u.name))
              } yield ()
            case Numeric(NumericCommand.RPL_ENDOFNAMES, args, prefix) =>
              ZIO.unit
            case NickChanged(oldNick, newNick) =>
              api.changeUserNick(oldNick, newNick)
            case Quit(userName, reason) =>
              for {
                user <- api.getUser(UserKey(userName))
                _    <- api.removeUser(user)
              } yield ()
            case UserLoggedIn(nick, accountName) =>
              for {
                user <- api.getUser(UserKey(nick))
                _    <- api.addUser(user.copy(accountName = Some(accountName)))
              } yield ()
            case UserLoggedOut(nick) =>
              for {
                user <- api.getUser(UserKey(nick))
                _    <- api.addUser(user.copy(accountName = None))
              } yield ()
          }
      handlers <- customHandlers.get
      _ <- ZIO.foreach(handlers)(handler =>
            handler
              .handle(event)
              .catchAll(e => putStrLn("Handlers error : " + e.getClass.getSimpleName + " " + e.getCause) *> ZIO.unit)
              .fork
          )
    } yield ()
}
