package votbot.model


package object irc {

  sealed trait Message
  final case class RawMessage(cmd: Command, args: Vector[String], prefix: Option[Prefix] = None) extends Message

  object RawMessage {

    def apply(cmd: Command, args: String*): RawMessage =
      if (args.size > 1)
        new RawMessage(cmd, args.toVector.updated(args.size - 1, ":" + args.last), None)
      else
        new RawMessage(cmd, args.toVector, None)
  }

  final case class Prefix(nick: String, username: String, host: String)

  case class ChannelKey(str: String) extends AnyVal

  object ChannelKey {
    def apply(str: String): ChannelKey = new ChannelKey(str.toLowerCase)
  }

  case class UserKey(str: String) extends AnyVal

  object UserKey {
    def apply(str: String): UserKey = new UserKey(str.toLowerCase)
  }

  final case class Channel(name: String, modes: List[ChannelMode], members: Set[UserKey])
  final case class ChannelMode(mode: String, args: Option[String])
  final case class User(name: String, channels: Set[ChannelKey])
}
