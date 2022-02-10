package votbot.event

import votbot.database.Database
import votbot.event.Event._
import votbot.model.irc._
import votbot.{ Api, BotState, Configuration }
import zio.{ IO, ZIO, ZLayer }
import zio.Random

object EventHandler {
  type EventHandler = EventHandler.Service

  trait Service {
    def handle(event: Event): IO[Throwable, Option[Unit]]
    protected def onPing: PartialFunction[Event, IO[Throwable, Unit]]
    protected def onConnected: PartialFunction[Event, IO[Throwable, Unit]]
    protected def onWelcome: PartialFunction[Event, IO[Throwable, Unit]]
    protected def onJoin: PartialFunction[Event, IO[Throwable, Unit]]
    protected def onPart: PartialFunction[Event, IO[Throwable, Unit]]
    protected def onCap: PartialFunction[Event, IO[Throwable, Unit]]
    protected def onNamesList: PartialFunction[Event, IO[Throwable, Unit]]
    protected def onNickChanged: PartialFunction[Event, IO[Throwable, Unit]]
    protected def onNumeric: PartialFunction[Event, IO[Throwable, Unit]]
    protected def onQuit: PartialFunction[Event, IO[Throwable, Unit]]
    protected def onUserLoggedIn: PartialFunction[Event, IO[Throwable, Unit]]
    protected def onUserLoggedOut: PartialFunction[Event, IO[Throwable, Unit]]

    def handleFunction: PartialFunction[Event, IO[Throwable, Unit]]
  }

  def handle(event: Event): ZIO[EventHandler, Throwable, Option[Unit]] =
    ZIO.environmentWithZIO[EventHandler](_.get.handle(event))

    case class DefaultEventHandler(
      api: Api.Service,
      cfg: Configuration.Service,
      database: Database.Service,
      botState: BotState.Service,
      random: Random) extends EventHandler.Service {

          val handlerFunctions = List(
            onWelcome,
            onConnected,
            onPing,
            onCap,
            onJoin,
            onPart,
            onNamesList,
            onNickChanged,
            onNumeric,
            onQuit,
            onUserLoggedIn,
            onUserLoggedOut
          )

          override val handleFunction: PartialFunction[Event, ZIO[Any, Throwable, Unit]] =
            handlerFunctions.foldLeft(PartialFunction.empty[Event, ZIO[Any, Throwable, Unit]])(_.orElse(_))

          override def handle(event: Event): ZIO[Any, Throwable, Option[Unit]] =
            ZIO.whenCase(event)(handleFunction)

          override def onPing: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case Ping(Some(args)) =>
              api.enqueueOutMessage(Message(Command.Pong, args))
            case Ping(None) =>
              api.enqueueOutMessage(Message(Command.Pong))
          }

          override def onConnected: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case Connected(_) =>
              val capLsCmd = Message(Command.CapLs)
              val nickCmd  = Message(Command.Nick, cfg.bot.nick)
              val userCmd  = Message(Command.User, cfg.bot.userName, "*", "*", cfg.bot.realName)
              for {
                _ <- api.enqueueOutMessage(capLsCmd)
                _ <- api.enqueueOutMessage(nickCmd)
                _ <- api.enqueueOutMessage(userCmd)
              } yield ()

          }

          override def onWelcome: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case Welcome(nick, _) =>
              for {
                chanSettings          <- database.channelSettingsRepo.getAll
                (enabledS, disabledS) = chanSettings.span(_.autoJoin == true)
                enabled               = enabledS.map(_.channelKey)
                disabled              = disabledS.map(_.channelKey)
                cfgAutoJoinAll        = cfg.bot.autoJoinChannels.map(ChannelKey(_))
                //exclude disabled
                autoJoinEnabled = (cfgAutoJoinAll.filterNot(disabled.contains(_)) ++ enabled).map(_.value)
                joinCmd         = Message(Command.Join, autoJoinEnabled.mkString(","))
                _               <- api.enqueueOutMessage(joinCmd) *> botState.setNick(nick)
              } yield ()
          }

          override def onJoin: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case BotJoin(chName) =>
              for {
                _ <- api
                      .addChannel(Channel(chName, List.empty, Set.empty, Set.empty, Event.emptyHandleFunction)) //fixme
              } yield ()

            case Join(userKey, channel) =>
              for {
                user <- api.getOrCreateUser(userKey.value)
                _    <- api.addUserToChannel(channel, user)
                _    <- api.addChannelToUser(channel, userKey)
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
          }

          override def onPart: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case BotPart(channels) => api.removeChannel(channels)
            case Part(user, channel, _) =>
              api.removeChannelMember(channel, user)
          }

          override def onCap: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case CapabilityList(supportedCaps) =>
              val capsFromCfg          = cfg.server.capRequire.getOrElse(List.empty).map(_.toLowerCase)
              val supportedAndRequired = capsFromCfg.intersect(supportedCaps.map(_.toLowerCase))
              val capReqCmd =
                Message(Command.CapReq, ":" + supportedAndRequired.mkString(" "))
              val capEnd = Message(Command.CapEnd)
              api.enqueueOutMessage(capReqCmd) *> api.enqueueOutMessage(capEnd)
            case CapabilityAck(caps) =>
              val capsSupported = caps
                .map(Capabilities.withNameInsensitiveOption)
                .filter(_.nonEmpty)
                .map(_.get)
              botState.addCapabilities(capsSupported: _*)
            case CapabilityNak(caps) =>
              val capsToRemove = caps
                .map(Capabilities.withNameInsensitiveOption)
                .filter(_.nonEmpty)
                .map(_.get)
              botState.removeCapabilities(capsToRemove: _*)
          }

          override def onNamesList: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case NamesList(channelKey, members) =>
              for {
                channelMembers <- ZIO.foreach(members) { tuple =>
                                   for {
                                     user  <- api.getOrCreateUser(tuple._1)
                                     modes = tuple._2
                                   } yield (user, modes)
                                 }
                _ <- ZIO.foreach(channelMembers)(t => api.addUserToChannel(channelKey, t._1))
                _ <- ZIO.foreach(channelMembers)(u => api.queryAccByNick(u._1.name))
              } yield ()
          }

          override def onNickChanged: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case NickChanged(oldNick, newNick) =>
              api.changeUserNick(oldNick, newNick)
          }

          override def onNumeric: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case Numeric(NumericCommand.ERR_NICKNAMEINUSE, _, _) =>
              for {
                n       <- random.nextInt
                newNick = cfg.bot.nick + n
                _       <- api.enqueueOutMessage(Message(Command.Nick, newNick))
                _       <- botState.setNick(newNick)
              } yield ()
          }

          override def onQuit: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case Quit(user, _) =>
              for {
                user <- api.getUser(user)
                _    <- api.removeUser(user)
              } yield ()
          }

          override def onUserLoggedIn: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case UserLoggedIn(user, accountName) =>
              for {
                user <- api.getUser(user)
                _    <- api.addUser(user.copy(accountName = Some(accountName)))
              } yield ()
          }

          override def onUserLoggedOut: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
            case UserLoggedOut(user) =>
              for {
                user <- api.getUser(user)
                _    <- api.addUser(user.copy(accountName = None))
              } yield ()
          }
        }

  val defaultEventHandler = (DefaultEventHandler(_, _, _, _, _)).toLayer
}
