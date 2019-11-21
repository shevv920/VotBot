package votbot

import votbot.event.Event.Event
import votbot.model.Irc
import votbot.model.Irc.{ Channel, ChannelMember, User }
import zio._

trait Api {
  protected val parseQ: Queue[String]
  protected val processQ: Queue[Irc.RawMessage]
  protected val outMessageQ: Queue[Irc.RawMessage]
  protected val eventQ: Queue[Event]
  protected val channels: Ref[Map[String, Channel]]
  protected val knownUsers: Ref[Set[User]]

  def enqueueEvent(evt: Event*): UIO[Unit]
  def enqueueProcess(msg: Irc.RawMessage*): UIO[Unit]
  def enqueueParse(raw: String*): UIO[Unit]
  def enqueueOutMessage(msg: Irc.RawMessage*): UIO[Unit]
  def dequeueEvent(): UIO[Event]
  def dequeueParse(): UIO[String]
  def dequeueOutMessage(): UIO[Irc.RawMessage]
  def dequeueProcess(): UIO[Irc.RawMessage]
  def sendChannelMessage(channel: String, msg: String): UIO[Unit]
  def sendPrivateMessage(nick: String, msg: String): UIO[Unit]
  def allChannels(): Task[List[Channel]]
  def addChannel(channel: Channel): UIO[Unit]
  def removeChannel(chName: String): UIO[Unit]
  def addChannelMember(chName: String, member: ChannelMember): Task[Unit]
  def removeChannelMember(chName: String, memberName: String): Task[Unit]
  def getChannel(chName: String): Task[Channel]
  def addUser(user: User): Task[Unit]
  def removeUser(userName: String): Task[Unit]
  def removeUser(user: User): Task[Unit]
  def getOrCreateUser(name: String): Task[User]
  def findUser(name: String): Task[Option[User]]
}

trait LiveApi extends Api {
  override def findUser(name: String): Task[Option[User]] =
    for {
      users <- knownUsers.get
      user = users.find(_.name.equalsIgnoreCase(name))
    } yield user
  override def getOrCreateUser(name: String): Task[User] =
    for {
      users <- knownUsers.get
      user <- ZIO.effect(
               users
                 .find(_.name.equalsIgnoreCase(name))
                 .getOrElse(User(name, Set.empty))
             )
      _ <- addUser(user)
    } yield user
  override def addUser(user: User): Task[Unit] =
    knownUsers.update(_ + user).unit
  override def removeUser(user: User): Task[Unit] =
    knownUsers.update(_ - user).unit
  override def removeUser(userName: String): Task[Unit] =
    knownUsers.update(users => users.filterNot(_.name.equalsIgnoreCase(userName))).unit
  override def removeChannelMember(chName: String, memberName: String): Task[Unit] =
    for {
      channel <- getChannel(chName)
      _ <- channels.update(
            m =>
              m + (chName -> channel.copy(
                members = channel.members.filterNot(_.user.name.equalsIgnoreCase(memberName))
              ))
          )
    } yield ()
  override def addChannelMember(chName: String, member: ChannelMember): Task[Unit] =
    for {
      channel <- getChannel(chName)
      nCh     = channel.copy(members = channel.members + member)
      _       <- channels.update(m => m + (chName -> nCh))
    } yield ()

  override def getChannel(chName: String): Task[Channel] =
    for {
      channels <- channels.get
      channel <- ZIO
                  .fromOption(channels.get(chName))
                  .mapError(_ => new Exception("Channel does not exist: " + chName))
    } yield channel
  override def removeChannel(channelName: String): UIO[Unit] =
    channels.update(_ - channelName).unit
  override def addChannel(channel: Channel): UIO[Unit] =
    channels.update(_ + (channel.name -> channel)).unit
  override def allChannels(): Task[List[Channel]] =
    channels.get.map(_.values.toList)
  override def enqueueEvent(evt: Event*): UIO[Unit] =
    eventQ.offerAll(evt).unit
  override def enqueueProcess(msg: Irc.RawMessage*): UIO[Unit] =
    processQ.offerAll(msg).unit
  override def enqueueParse(raw: String*): UIO[Unit] =
    parseQ.offerAll(raw).unit
  override def enqueueOutMessage(msg: Irc.RawMessage*): UIO[Unit] =
    outMessageQ.offerAll(msg).unit
  override def dequeueEvent(): UIO[Event] =
    eventQ.take
  override def dequeueParse(): UIO[String] =
    parseQ.take
  override def dequeueProcess(): UIO[Irc.RawMessage] =
    processQ.take
  override def dequeueOutMessage(): UIO[Irc.RawMessage] =
    outMessageQ.take
  override def sendChannelMessage(channel: String, msg: String): UIO[Unit] =
    outMessageQ.offer(Irc.RawMessage(Irc.Command.Privmsg, channel, msg)).unit
  override def sendPrivateMessage(nick: String, msg: String): UIO[Unit] =
    outMessageQ.offer(Irc.RawMessage(Irc.Command.Privmsg, nick, msg)).unit
}
