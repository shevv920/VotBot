package votbot

import votbot.event.Event
import votbot.model.irc.{ Channel, ChannelKey, Command, Message, User, UserKey }
import zio._

object Api {
  type Api = Has[Api.Service]

  trait Service {

    def enqueueEvent(evt: Event): IO[Nothing, Unit]
    def enqueueParsed(msg: Message): IO[Nothing, Unit]
    def enqueueReceived(raw: String): IO[Nothing, Unit]
    def enqueueOutMessage(msg: Message): IO[Nothing, Unit]

    def dequeueEvent(): IO[Nothing, Event]
    def dequeueReceived(): IO[Nothing, String]
    def dequeueOutMessage(): IO[Nothing, Message]
    def dequeueAllOutMessages(): IO[Nothing, List[Message]]
    def dequeueParsedMessage(): IO[Nothing, Message]

    def sendChannelMessage(channel: ChannelKey, msg: String): IO[Nothing, Unit]
    def sendPrivateMessage(nick: String, msg: String): IO[Nothing, Unit]
    def allChannels(): IO[Throwable, List[Channel]]
    def addChannel(channel: Channel): IO[Nothing, Unit]
    def removeChannel(chKey: ChannelKey): IO[Nothing, Unit]
    def addUserToChannel(chKey: ChannelKey, userKey: UserKey): IO[Throwable, Unit]
    def addUserToChannel(chKey: ChannelKey, user: User): IO[Throwable, Unit]
    def removeChannelMember(chKey: ChannelKey, userKey: UserKey): IO[Throwable, Unit]
    def getChannel(chKey: ChannelKey): IO[Throwable, Channel]
    def addUser(user: User): IO[Throwable, Unit]
    def removeUser(userName: UserKey): IO[Throwable, Unit]
    def removeUser(user: User): IO[Throwable, Unit]
    def getOrCreateUser(name: String): IO[Throwable, User]
    def findUser(name: String): IO[Throwable, Option[User]]
    def getUser(name: UserKey): IO[Throwable, User]
    def addChannelToUser(chKey: ChannelKey, uKey: UserKey): IO[Throwable, Unit]
    def removeChannelFromUser(chKey: ChannelKey, uKey: UserKey): IO[Throwable, Unit]
    def isUserLoggedIn(userKey: UserKey): IO[Throwable, Boolean]
    def getUserAccountName(userKey: UserKey): IO[Throwable, String]
    def queryAccByNick(name: String): IO[Throwable, Unit]
    def changeUserNick(oldNick: String, newNick: String): IO[Throwable, Unit]
    def findChannel(channelKey: ChannelKey): IO[Throwable, Option[Channel]]
    def leaveChannel(channelKey: ChannelKey, reason: String = ""): IO[Throwable, Unit]
    def joinChannel(channelName: ChannelKey): IO[Throwable, Unit]
    def registerChannelHandler(channelKey: ChannelKey, handler: Event.Handler): IO[Throwable, Unit]
    def deregisterChannelHandler(channelKey: ChannelKey, handler: Event.Handler): IO[Throwable, Unit]
  }

  def enqueueEvent(evt: Event): ZIO[Api, Nothing, Unit] =
    ZIO.accessM[Api](_.get.enqueueEvent(evt))

  def enqueueParsed(msg: Message): ZIO[Api, Nothing, Unit] =
    ZIO.accessM[Api](_.get.enqueueParsed(msg))

  def enqueueReceived(raw: String): ZIO[Api, Nothing, Unit] =
    ZIO.accessM[Api](_.get.enqueueReceived(raw))

  def enqueueOutMessage(msg: Message): ZIO[Api, Nothing, Unit] =
    ZIO.accessM[Api](_.get.enqueueOutMessage(msg))

  def dequeueEvent(): ZIO[Api, Nothing, Event] =
    ZIO.accessM[Api](_.get.dequeueEvent())

  def dequeueReceived(): ZIO[Api, Nothing, String] =
    ZIO.accessM[Api](_.get.dequeueReceived())

  def dequeueOutMessage(): ZIO[Api, Nothing, Message] =
    ZIO.accessM[Api](_.get.dequeueOutMessage())

  def dequeueAllOutMessages(): ZIO[Api, Nothing, List[Message]] =
    ZIO.accessM[Api](_.get.dequeueAllOutMessages())

  def dequeueParsedMessage(): ZIO[Api, Nothing, Message] =
    ZIO.accessM[Api](_.get.dequeueParsedMessage())

  val defaultApi: ZLayer.NoDeps[Nothing, Api] = ZLayer.fromEffect {
    for {
      rcvdQ      <- Queue.unbounded[String]
      parsedMsgQ <- Queue.unbounded[Message]
      outMsgQ    <- Queue.unbounded[Message]
      evtQ       <- Queue.unbounded[Event]
      kChannels  <- Ref.make[Map[ChannelKey, Channel]](Map.empty)
      kUsers     <- Ref.make[Map[UserKey, User]](Map.empty)
    } yield new DefaultApi {
      override protected val receivedQ: Queue[String]                     = rcvdQ
      override protected val parsedMessageQ: Queue[Message]               = parsedMsgQ
      override protected val outMessageQ: Queue[Message]                  = outMsgQ
      override protected val eventQ: Queue[Event]                         = evtQ
      override protected val knownChannels: Ref[Map[ChannelKey, Channel]] = kChannels
      override protected val knownUsers: Ref[Map[UserKey, User]]          = kUsers
    }
  }
}

trait DefaultApi extends Api.Service {

  protected val receivedQ: Queue[String]
  protected val parsedMessageQ: Queue[Message]
  protected val outMessageQ: Queue[Message]
  protected val eventQ: Queue[Event]
  protected val knownChannels: Ref[Map[ChannelKey, Channel]]
  protected val knownUsers: Ref[Map[UserKey, User]]

  override def deregisterChannelHandler(channelKey: ChannelKey, handler: Event.Handler): IO[Throwable, Unit] =
    for {
      channel        <- getChannel(channelKey)
      handlers       = channel.handlers - handler
      newHandle      = handlers.map(_.handleFunction).foldLeft(Event.emptyHandleFunction)(_.orElse(_))
      channelUpdated = channel.copy(handleFunction = newHandle)
      _              <- addChannel(channelUpdated)
    } yield ()

  override def registerChannelHandler(channelKey: ChannelKey, handler: Event.Handler): IO[Throwable, Unit] =
    for {
      channel        <- getChannel(channelKey)
      handlers       = channel.handlers + handler
      newHandle      = handlers.map(_.handleFunction).foldLeft(Event.emptyHandleFunction)(_.orElse(_))
      channelUpdated = channel.copy(handleFunction = newHandle)
      _              <- addChannel(channelUpdated)
    } yield ()

  override def joinChannel(channelName: ChannelKey): IO[Throwable, Unit] =
    for {
      channel <- findChannel(channelName)
      _ <- ZIO.when(channel.isEmpty) {
            enqueueOutMessage(Message(Command.Join, channelName.value))
          }
    } yield ()

  override def leaveChannel(channelKey: ChannelKey, reason: String = ""): IO[Throwable, Unit] =
    for {
      channel <- findChannel(channelKey)
      _ <- ZIO.whenM(ZIO.effectTotal(channel.nonEmpty)) {
            enqueueOutMessage(Message(Command.Part, channel.get.name))
          }
    } yield ()

  override def findChannel(channelKey: ChannelKey): IO[Throwable, Option[Channel]] =
    knownChannels.get.map(_.get(channelKey))

  override def changeUserNick(oldNick: String, newNick: String): IO[Throwable, Unit] =
    for {
      oldUser <- getUser(UserKey(oldNick))
      newUser = oldUser.copy(name = newNick, accountName = None)
      _       <- addUser(newUser)
      _       <- removeUser(oldUser)
      _       <- ZIO.foreach(newUser.channels)(ch => addUserToChannel(ch, newUser))
    } yield ()

  override def queryAccByNick(name: String): IO[Throwable, Unit] =
    enqueueOutMessage(Message(Command.Who, List(name, "+n%na")))

  override def getUserAccountName(userKey: UserKey): IO[Throwable, String] =
    getUser(userKey).map(_.accountName.getOrElse("*"))

  override def isUserLoggedIn(userKey: UserKey): IO[Throwable, Boolean] =
    getUser(userKey).map(_.accountName.nonEmpty)

  override def removeChannelFromUser(chName: ChannelKey, uName: UserKey): IO[Throwable, Unit] =
    for {
      user <- getUser(uName)
      chs  = user.channels.filterNot(_ == chName)
      _    <- knownUsers.update(us => us + (uName -> user.copy(channels = chs)))
    } yield ()

  override def addChannelToUser(chKey: ChannelKey, uName: UserKey): IO[Throwable, Unit] =
    for {
      ch    <- getChannel(chKey)
      user  <- getUser(uName)
      nUser = user.copy(channels = user.channels + ChannelKey(ch.name))
      _     <- knownUsers.update(us => us + (uName -> nUser))
    } yield ()

  override def getUser(uKey: UserKey): IO[Throwable, User] =
    for {
      users <- knownUsers.get
      user <- ZIO
               .fromOption(users.get(uKey))
               .mapError(_ => new Exception("User does not exists " + uKey))
    } yield user

  override def findUser(name: String): IO[Throwable, Option[User]] =
    for {
      users <- knownUsers.get
      user  = users.get(UserKey(name))
    } yield user

  override def getOrCreateUser(name: String): IO[Throwable, User] =
    for {
      users <- knownUsers.get
      user  <- ZIO.effect(users.getOrElse(UserKey(name), User(name, Set.empty)))
      _     <- addUser(user)
    } yield user

  override def addUser(user: User): IO[Throwable, Unit] =
    knownUsers.update(m => m + (UserKey(user.name) -> user)).unit

  override def removeUser(user: User): IO[Throwable, Unit] =
    for {
      _ <- ZIO.foreach(user.channels) { chName =>
            removeChannelMember(chName, UserKey(user.name))
          }
      _ <- knownUsers.update(u => u - UserKey(user.name))
    } yield ()

  override def removeUser(userName: UserKey): IO[Throwable, Unit] =
    getUser(userName)
      .map(user => removeUser(user))

  override def removeChannelMember(chName: ChannelKey, memberName: UserKey): IO[Throwable, Unit] =
    for {
      channel <- getChannel(chName)
      user    <- getUser(memberName)
      _       <- knownChannels.update(m => m + (chName -> channel.copy(members = channel.members - UserKey(user.name))))
      _       <- removeChannelFromUser(chName, memberName)
    } yield ()

  override def addUserToChannel(chKey: ChannelKey, uKey: UserKey): IO[Throwable, Unit] =
    for {
      user <- getOrCreateUser(uKey.value)
      _    <- addUserToChannel(chKey, user)
    } yield ()

  override def addUserToChannel(chKey: ChannelKey, user: User): IO[Throwable, Unit] =
    for {
      channel <- getChannel(chKey)
      nCh     = channel.copy(members = channel.members + UserKey(user.name))
      _       <- knownChannels.update(m => m + (chKey -> nCh))
    } yield ()

  override def getChannel(chName: ChannelKey): IO[Throwable, Channel] =
    for {
      channels <- knownChannels.get
      channel <- ZIO
                  .fromOption(channels.get(chName))
                  .mapError(_ => new Exception("Channel does not exist: " + chName))
    } yield channel

  override def removeChannel(channelName: ChannelKey): UIO[Unit] =
    knownChannels.update(_ - channelName).unit

  override def addChannel(channel: Channel): UIO[Unit] =
    knownChannels.update(_ + (ChannelKey(channel.name) -> channel)).unit

  override def allChannels(): IO[Throwable, List[Channel]] =
    knownChannels.get.map(_.values.toList)

  override def enqueueEvent(evt: Event): UIO[Unit] =
    eventQ.offer(evt).unit

  override def enqueueParsed(msg: Message): UIO[Unit] =
    parsedMessageQ.offer(msg).unit

  override def enqueueReceived(raw: String): UIO[Unit] =
    receivedQ.offer(raw).unit

  override def enqueueOutMessage(msg: Message): UIO[Unit] =
    outMessageQ.offer(msg).unit

  override def dequeueEvent(): UIO[Event] =
    eventQ.take

  override def dequeueReceived(): UIO[String] =
    receivedQ.take

  override def dequeueParsedMessage(): UIO[Message] =
    parsedMessageQ.take

  override def dequeueOutMessage(): UIO[Message] =
    outMessageQ.take

  override def dequeueAllOutMessages(): UIO[List[Message]] =
    outMessageQ.takeAll

  override def sendChannelMessage(channel: ChannelKey, msg: String): UIO[Unit] =
    outMessageQ.offer(Message(Command.Privmsg, channel.value, msg)).unit

  override def sendPrivateMessage(nick: String, msg: String): UIO[Unit] =
    outMessageQ.offer(Message(Command.Privmsg, nick, msg)).unit

}
