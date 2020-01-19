package votbot.event

import votbot.database.{ ChannelSettingsRepo, Database }
import votbot.event.Event._
import votbot.model.irc._
import votbot.{ Api, BotState, Configuration }
import zio.ZIO
import zio.random.Random

trait EventHandler {
  val eventHandler: EventHandler.Service[Any]
}

object EventHandler {

  trait Service[R] {
    def handle(event: Event): ZIO[R, Throwable, Unit]
    protected def onPing: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    protected def onConnected: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    protected def onWelcome: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    protected def onJoin: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    protected def onPart: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    protected def onCap: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    protected def onNamesList: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    protected def onNickChanged: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    protected def onNumeric: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    protected def onQuit: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    protected def onUserLoggedIn: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    protected def onUserLoggedOut: PartialFunction[Event, ZIO[R, Throwable, Unit]]

    def handleFunction: PartialFunction[Event, ZIO[R, Throwable, Unit]]
  }
}

trait DefaultEventHandler extends EventHandler {
  val configuration: Configuration.Service[Any]
  val api: Api.Service[Any]
  val botState: BotState.Service[Any]
  val random: Random.Service[Any]
  val database: Database.Service[Any]

  override val eventHandler: EventHandler.Service[Any] = new EventHandler.Service[Any] {

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

    override def handle(event: Event): ZIO[Any, Throwable, Unit] =
      for {
        _ <- ZIO.whenCase(event)(handleFunction)
      } yield ()

    override def onPing: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case Ping(Some(args)) =>
        api.enqueueOutMessage(Message(Command.Pong, args))
      case Ping(None) =>
        api.enqueueOutMessage(Message(Command.Pong))
    }

    override def onConnected: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case Connected(_) =>
        val capLsCmd = Message(Command.CapLs)
        val nickCmd  = Message(Command.Nick, configuration.bot.nick)
        val userCmd = Message(
          Command.User,
          configuration.bot.userName,
          "*",
          "*",
          configuration.bot.realName
        )
        api.enqueueOutMessage(capLsCmd) *> api.enqueueOutMessage(nickCmd) *> api.enqueueOutMessage(userCmd)
    }

    override def onWelcome: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case Welcome(nick, _) =>
        for {
          dbAutoJoinChannels <- database.channelSettingsRepo.findAutoJoinEnabled()
          joinCmd            = Message(Command.Join, dbAutoJoinChannels.mkString(","))
          _                  <- api.enqueueOutMessage(joinCmd).flatMap(_ => botState.setNick(nick))
        } yield ()
    }

    override def onJoin: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case BotJoin(chName) =>
        api.addChannel(Channel(chName, List.empty, Set.empty))
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
        val capsFromCfg          = configuration.server.capRequire.getOrElse(List.empty).map(_.toLowerCase)
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
      case NamesList(channel, members) =>
        for {
          currentNick <- botState.currentNick()
          //filter out bot from names list
          channelMembers <- ZIO.foreach(members.filterNot(_._1.equalsIgnoreCase(currentNick))) { tuple =>
                             for {
                               user  <- api.getOrCreateUser(tuple._1)
                               modes = tuple._2
                             } yield user
                           }
          _ <- ZIO.foreach(channelMembers)(api.addUserToChannel(channel, _))
          _ <- ZIO.foreach(channelMembers)(u => api.askForAccByName(u.name))
        } yield ()
    }

    override def onNickChanged: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case NickChanged(oldNick, newNick) =>
        api.changeUserNick(oldNick, newNick)
    }

    override def onNumeric: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case Numeric(NumericCommand.ERR_NICKNAMEINUSE, _, _) =>
        for {
          n       <- random.nextInt(99)
          newNick = configuration.bot.nick + n
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
}
