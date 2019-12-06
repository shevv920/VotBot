package votbot.model

package object irc {

  final case class ChannelKey(str: String) extends AnyVal

  object ChannelKey {
    def apply(str: String): ChannelKey = new ChannelKey(str.toLowerCase)
  }

  final case class UserKey(str: String) extends AnyVal

  object UserKey {
    def apply(str: String): UserKey = new UserKey(str.toLowerCase)
  }

  final case class Channel(name: String, modes: List[ChannelMode], members: Set[UserKey])
  final case class ChannelMode(mode: String, args: Option[String])
  final case class User(name: String, channels: Set[ChannelKey], accountName: Option[String] = None)
}
