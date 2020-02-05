package votbot.model.irc

final case class User(name: String, channels: Set[ChannelKey], accountName: Option[String] = None)
