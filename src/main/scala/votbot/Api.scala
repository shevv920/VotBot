package votbot

import votbot.event.Event.Event
import votbot.model.irc.{ Channel, ChannelKey, Command, RawMessage, User, UserKey }
import zio._

trait Api {
  val api: Api.Service[Any]
}

object Api {

  trait Service[R] {
    protected val parseQ: Queue[String]
    protected val processQ: Queue[RawMessage]
    protected val outMessageQ: Queue[RawMessage]
    protected val eventQ: Queue[Event]
    protected val knownChannels: Ref[Map[ChannelKey, Channel]]
    protected val knownUsers: Ref[Map[UserKey, User]]

    def enqueueEvent(evt: Event*): UIO[Unit]
    def enqueueProcess(msg: RawMessage*): UIO[Unit]
    def enqueueParse(raw: String*): UIO[Unit]
    def enqueueOutMessage(msg: RawMessage*): UIO[Unit]

    def dequeueEvent(): UIO[Event]
    def dequeueParse(): UIO[String]
    def dequeueOutMessage(): UIO[RawMessage]
    def dequeueAllOutMessages(): UIO[List[RawMessage]]
    def dequeueProcess(): UIO[RawMessage]

    def sendChannelMessage(channel: String, msg: String): UIO[Unit]
    def sendPrivateMessage(nick: String, msg: String): UIO[Unit]
    def allChannels(): Task[List[Channel]]
    def addChannel(channel: Channel): UIO[Unit]
    def removeChannel(chKey: ChannelKey): UIO[Unit]
    def addChannelMember(chKey: ChannelKey, userKey: UserKey): Task[Unit]
    def addChannelMember(chKey: ChannelKey, user: User): Task[Unit]
    def removeChannelMember(chKey: ChannelKey, userKey: UserKey): Task[Unit]
    def getChannel(chKey: ChannelKey): Task[Channel]
    def addUser(user: User): Task[Unit]
    def removeUser(userName: UserKey): Task[Unit]
    def removeUser(user: User): Task[Unit]
    def getOrCreateUser(name: String): Task[User]
    def findUser(name: String): Task[Option[User]]
    def getUser(name: UserKey): Task[User]
    def addChannelToUser(chKey: ChannelKey, uKey: UserKey): Task[Unit]
    def removeChannelFromUser(chKey: ChannelKey, uKey: UserKey): Task[Unit]
    def isUserLoggedIn(userKey: UserKey): Task[Boolean]
    def getUserAccountName(userKey: UserKey): Task[String]
    def askForAccByName(name: String): Task[Unit]
  }
}

trait DefaultApi[R] extends Api.Service[R] {

  override def askForAccByName(name: String): Task[Unit] =
    enqueueOutMessage(RawMessage(Command.Who, Vector(name, "+n%na")))

  override def getUserAccountName(userKey: UserKey): Task[String] =
    getUser(userKey).map(_.accountName.getOrElse("*"))

  override def isUserLoggedIn(userKey: UserKey): Task[Boolean] =
    getUser(userKey).map(_.accountName.nonEmpty)

  override def removeChannelFromUser(chName: ChannelKey, uName: UserKey): Task[Unit] =
    for {
      user <- getUser(uName)
      chs  = user.channels.filterNot(_ == chName)
      _    <- knownUsers.update(us => us + (uName -> user.copy(channels = chs)))
    } yield ()

  override def addChannelToUser(chKey: ChannelKey, uName: UserKey): Task[Unit] =
    for {
      ch    <- getChannel(chKey)
      user  <- getUser(uName)
      nUser = user.copy(channels = user.channels + ChannelKey(ch.name))
      _     <- knownUsers.update(us => us + (uName -> nUser))
    } yield ()

  override def getUser(uKey: UserKey): Task[User] =
    for {
      users <- knownUsers.get
      user <- ZIO
               .fromOption(users.get(uKey))
               .mapError(_ => new Exception("User does not exists " + uKey))
    } yield user

  override def findUser(name: String): Task[Option[User]] =
    for {
      users <- knownUsers.get
      user  = users.get(UserKey(name))
    } yield user

  override def getOrCreateUser(name: String): Task[User] =
    for {
      users <- knownUsers.get
      user  <- ZIO.effect(users.getOrElse(UserKey(name), User(name, Set.empty)))
      _     <- addUser(user)
    } yield user

  override def addUser(user: User): Task[Unit] =
    knownUsers.update(m => m + (UserKey(user.name) -> user)).unit

  override def removeUser(user: User): Task[Unit] =
    for {
      _ <- ZIO.foreach(user.channels) { chName =>
            removeChannelMember(chName, UserKey(user.name))
          }
      _ <- knownUsers.update(u => u - UserKey(user.name))
    } yield ()

  override def removeUser(userName: UserKey): Task[Unit] =
    getUser(userName)
      .map(user => removeUser(user))

  override def removeChannelMember(chName: ChannelKey, memberName: UserKey): Task[Unit] =
    for {
      channel <- getChannel(chName)
      user    <- getUser(memberName)
      _       <- knownChannels.update(m => m + (chName -> channel.copy(members = channel.members - UserKey(user.name))))
      _       <- removeChannelFromUser(chName, memberName)
    } yield ()

  override def addChannelMember(chKey: ChannelKey, uKey: UserKey): Task[Unit] =
    for {
      channel <- getChannel(chKey)
      user    <- getOrCreateUser(uKey.str)
      nCh     = channel.copy(members = channel.members + UserKey(user.name))
      _       <- knownChannels.update(m => m + (chKey -> nCh))
    } yield ()

  override def addChannelMember(chKey: ChannelKey, member: User): Task[Unit] =
    addChannelMember(chKey, UserKey(member.name))

  override def getChannel(chName: ChannelKey): Task[Channel] =
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

  override def allChannels(): Task[List[Channel]] =
    knownChannels.get.map(_.values.toList)

  override def enqueueEvent(evt: Event*): UIO[Unit] =
    eventQ.offerAll(evt).unit

  override def enqueueProcess(msg: RawMessage*): UIO[Unit] =
    processQ.offerAll(msg).unit

  override def enqueueParse(raw: String*): UIO[Unit] =
    parseQ.offerAll(raw).unit

  override def enqueueOutMessage(msg: RawMessage*): UIO[Unit] =
    outMessageQ.offerAll(msg).unit

  override def dequeueEvent(): UIO[Event] =
    eventQ.take

  override def dequeueParse(): UIO[String] =
    parseQ.take

  override def dequeueProcess(): UIO[RawMessage] =
    processQ.take

  override def dequeueOutMessage(): UIO[RawMessage] =
    outMessageQ.take

  override def dequeueAllOutMessages(): UIO[List[RawMessage]] =
    outMessageQ.takeAll

  override def sendChannelMessage(channel: String, msg: String): UIO[Unit] =
    outMessageQ.offer(RawMessage(Command.Privmsg, channel, msg)).unit

  override def sendPrivateMessage(nick: String, msg: String): UIO[Unit] =
    outMessageQ.offer(RawMessage(Command.Privmsg, nick, msg)).unit

}
