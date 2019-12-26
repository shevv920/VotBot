package votbot.event.handlers
import votbot.event.Event._
import votbot.model.irc._
import votbot.{ Api, BotState, Configuration }
import zio.random.Random
import zio.ZIO

trait BaseEventHandler {
  val baseEventHandler: BaseEventHandler.Service[Any]
}

object BaseEventHandler {

  trait Service[R] {
    def handle(event: Event): ZIO[R, Throwable, Unit]
    def onPing: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    def onConnected: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    def onWelcome: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    def onJoin: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    def onPart: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    def onCap: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    def onNamesList: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    def onNickChanged: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    def onNumeric: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    def onQuit: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    def onUserLoggedIn: PartialFunction[Event, ZIO[R, Throwable, Unit]]
    def onUserLoggedOut: PartialFunction[Event, ZIO[R, Throwable, Unit]]
  }
}

trait DefaultEventHandler extends BaseEventHandler {
  val configuration: Configuration.Service[Any]
  val api: Api.Service[Any]
  val botState: BotState.Service[Any]
  val random: Random.Service[Any]

  override val baseEventHandler: BaseEventHandler.Service[Any] = new BaseEventHandler.Service[Any] {

    private val handlerFunctions = List(
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
    private val handleFunction = handlerFunctions.tail.foldLeft(handlerFunctions.head)(_.orElse(_))

    override def handle(event: Event): ZIO[Any, Throwable, Unit] =
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
        val nickCmd  = Message(Command.Nick, configuration.bot.nick)
        val userCmd = Message(
          Command.User,
          configuration.bot.userName,
          "*",
          "*",
          configuration.bot.realName
        )
        api.enqueueOutMessage(capLsCmd, nickCmd, userCmd)
    }

    override def onWelcome: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case Welcome(nick, host) =>
        val joinCmd = Message(Command.Join, configuration.bot.autoJoinChannels.mkString(","))
        api
          .enqueueOutMessage(joinCmd)
          .flatMap(_ => botState.setNick(nick))
    }

    override def onJoin: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case Part(user, channel, reason) =>
        api.removeChannelMember(ChannelKey(channel), UserKey(user))
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
    }

    override def onPart: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case BotPart(channel) => api.removeChannel(ChannelKey(channel))
      case Part(user, channel, reason) =>
        api.removeChannelMember(ChannelKey(channel), UserKey(user))
    }

    override def onCap: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case CapabilityList(supportedCaps) =>
        val capsFromCfg          = configuration.server.capRequire.getOrElse(List.empty).map(_.toLowerCase)
        val supportedAndRequired = capsFromCfg.intersect(supportedCaps.map(_.toLowerCase))
        val capReqCmd =
          Message(Command.CapReq, ":" + supportedAndRequired.mkString(" "))
        val capEnd = Message(Command.CapEnd)
        api.enqueueOutMessage(capReqCmd, capEnd)
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
      case NamesList(chName, members) =>
        for {
          currentNick <- botState.currentNick()
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
    }

    override def onNickChanged: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case NickChanged(oldNick, newNick) =>
        api.changeUserNick(oldNick, newNick)
    }

    override def onNumeric: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case Numeric(NumericCommand.ERR_NICKNAMEINUSE, _, prefix) =>
        for {
          n       <- random.nextInt(99)
          newNick = configuration.bot.nick + n
          _       <- api.enqueueOutMessage(Message(Command.Nick, newNick))
          _       <- botState.setNick(newNick)
        } yield ()
    }

    override def onQuit: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case Quit(userName, reason) =>
        for {
          user <- api.getUser(UserKey(userName))
          _    <- api.removeUser(user)
        } yield ()
    }

    override def onUserLoggedIn: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case UserLoggedIn(nick, accountName) =>
        for {
          user <- api.getUser(UserKey(nick))
          _    <- api.addUser(user.copy(accountName = Some(accountName)))
        } yield ()
    }

    override def onUserLoggedOut: PartialFunction[Event, ZIO[Any, Throwable, Unit]] = {
      case UserLoggedOut(nick) =>
        for {
          user <- api.getUser(UserKey(nick))
          _    <- api.addUser(user.copy(accountName = None))
        } yield ()
    }
  }
}
