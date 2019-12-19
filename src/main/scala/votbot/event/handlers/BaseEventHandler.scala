package votbot.event.handlers
import votbot.event.Event._
import votbot.event.EventHandler
import votbot.model.irc._
import votbot.{ Api, BotState, Configuration }
import zio.random.Random
import zio.{ Ref, ZIO }

trait BaseEventHandler {
  val baseEventHandler: BaseEventHandler.Service[Any]
  val customHandlers: Ref[Set[EventHandler]]
}

object BaseEventHandler {

  trait Service[R] {
    def handle(event: Event): ZIO[R, Throwable, Unit]
    def addCustom(handler: EventHandler): ZIO[R, Throwable, Unit]
    def removeCustom(handler: EventHandler): ZIO[R, Throwable, Unit]
  }
}

trait DefaultEventHandler extends BaseEventHandler {
  val configuration: Configuration.Service[Any]
  val api: Api.Service[Any]
  val botState: BotState.Service[Any]
  val random: Random.Service[Any]

  override val baseEventHandler: BaseEventHandler.Service[Any] = new BaseEventHandler.Service[Any] {

    def addCustom(handler: EventHandler): ZIO[Any, Throwable, Unit] =
      customHandlers.update(hs => hs + handler).unit

    def removeCustom(handler: EventHandler): ZIO[Any, Throwable, Unit] =
      customHandlers.update(hs => hs.excl(handler)).unit

    override def handle(event: Event): ZIO[Any, Throwable, Unit] =
      for {
        _ <- ZIO.whenCase(event) {
              case Ping(Some(args)) =>
                api.enqueueOutMessage(Message(Command.Pong, args))
              case Ping(None) =>
                api.enqueueOutMessage(Message(Command.Pong))
              case Numeric(NumericCommand.RPL_YOURHOST, _, prefix) =>
                ZIO.unit
              case Numeric(NumericCommand.ERR_NICKNAMEINUSE, _, prefix) =>
                for {
                  n       <- random.nextInt(99)
                  newNick = configuration.config.bot.nick + n
                  _       <- api.enqueueOutMessage(Message(Command.Nick, newNick))
                  _       <- botState.setNick(newNick)
                } yield ()
              case Connected(remote) =>
                val capLsCmd = Message(Command.CapLs)
                val nickCmd  = Message(Command.Nick, configuration.config.bot.nick)
                val userCmd = Message(
                  Command.User,
                  configuration.config.bot.userName,
                  "*",
                  "*",
                  configuration.config.bot.realName
                )
                api.enqueueOutMessage(capLsCmd, nickCmd, userCmd)
              case CapabilityList(supportedCaps) =>
                val capsFromCfg          = configuration.config.server.capRequire.getOrElse(List.empty).map(_.toLowerCase)
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
              case Welcome(nick, host) =>
                val joinCmd = Message(Command.Join, configuration.config.bot.autoJoinChannels.mkString(","))
                api
                  .enqueueOutMessage(joinCmd)
                  .flatMap(_ => botState.setNick(nick))
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
      } yield ()
  }
}
