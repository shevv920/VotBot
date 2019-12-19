package votbot

import votbot.event.Event.Event
import votbot.model.irc.{ Channel, ChannelKey, Command, Message, User, UserKey }
import zio._

trait Api {
  val api: Api.Service[Any]
}

object Api {

  trait Service[R] {
    protected val receivedQ: Queue[String]
    protected val parsedMessageQ: Queue[Message]
    protected val outMessageQ: Queue[Message]
    protected val eventQ: Queue[Event]
    protected val knownChannels: Ref[Map[ChannelKey, Channel]]
    protected val knownUsers: Ref[Map[UserKey, User]]

    def enqueueEvent(evt: Event*): UIO[Unit]
    def enqueueParsed(msg: Message*): UIO[Unit]
    def enqueueReceived(raw: String*): UIO[Unit]
    def enqueueOutMessage(msg: Message*): UIO[Unit]

    def dequeueEvent(): UIO[Event]
    def dequeueReceived(): UIO[String]
    def dequeueOutMessage(): UIO[Message]
    def dequeueAllOutMessages(): UIO[List[Message]]
    def dequeueParsedMessage(): UIO[Message]

    def sendChannelMessage(channel: String, msg: String): UIO[Unit]
    def sendPrivateMessage(nick: String, msg: String): UIO[Unit]
    def allChannels(): Task[List[Channel]]
    def addChannel(channel: Channel): UIO[Unit]
    def removeChannel(chKey: ChannelKey): UIO[Unit]
    def addUserToChannel(chKey: ChannelKey, userKey: UserKey): Task[Unit]
    def addUserToChannel(chKey: ChannelKey, user: User): Task[Unit]
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
    def changeUserNick(oldNick: String, newNick: String): Task[Unit]
    def findChannel(channelKey: ChannelKey): Task[Option[Channel]]
    def leaveChannel(channelKey: ChannelKey): Task[Unit]
    def joinChannel(channelName: String): Task[Unit]
  }
}

trait DefaultApi[R] extends Api.Service[R] {

  override def joinChannel(channelName: String): Task[Unit] =
    for {
      channel <- findChannel(ChannelKey(channelName))
      _ <- ZIO.when(channel.isEmpty) {
            enqueueOutMessage(Message(Command.Join, channelName))
          }
    } yield ()

  override def leaveChannel(channelKey: ChannelKey): Task[Unit] =
    for {
      channel <- findChannel(channelKey)
      _ <- ZIO.when(channel.nonEmpty) {
            enqueueOutMessage(Message(Command.Part, channel.get.name))
          }
    } yield ()

  override def findChannel(channelKey: ChannelKey): Task[Option[Channel]] =
    knownChannels.get.map(_.get(channelKey))

  override def changeUserNick(oldNick: String, newNick: String): Task[Unit] =
    for {
      oldUser <- getUser(UserKey(oldNick))
      newUser = oldUser.copy(name = newNick, accountName = None)
      _       <- addUser(newUser)
      _       <- removeUser(oldUser)
      _       <- ZIO.foreach(newUser.channels)(ch => addUserToChannel(ch, newUser))
    } yield ()

  override def askForAccByName(name: String): Task[Unit] =
    enqueueOutMessage(Message(Command.Who, Vector(name, "+n%na")))

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

  override def addUserToChannel(chKey: ChannelKey, uKey: UserKey): Task[Unit] =
    for {
      user <- getOrCreateUser(uKey.value)
      _    <- addUserToChannel(chKey, user)
    } yield ()

  override def addUserToChannel(chKey: ChannelKey, user: User): Task[Unit] =
    for {
      channel <- getChannel(chKey)
      nCh     = channel.copy(members = channel.members + UserKey(user.name))
      _       <- knownChannels.update(m => m + (chKey -> nCh))
    } yield ()

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

  override def enqueueParsed(msg: Message*): UIO[Unit] =
    parsedMessageQ.offerAll(msg).unit

  override def enqueueReceived(raw: String*): UIO[Unit] =
    receivedQ.offerAll(raw).unit

  override def enqueueOutMessage(msg: Message*): UIO[Unit] =
    outMessageQ.offerAll(msg).unit

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

  override def sendChannelMessage(channel: String, msg: String): UIO[Unit] =
    outMessageQ.offer(Message(Command.Privmsg, channel, msg)).unit

  override def sendPrivateMessage(nick: String, msg: String): UIO[Unit] =
    outMessageQ.offer(Message(Command.Privmsg, nick, msg)).unit

}
