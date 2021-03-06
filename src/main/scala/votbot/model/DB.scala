package votbot.model

import votbot.model.irc.ChannelKey

object DB {
  final case class ChannelHandler(channelKey: ChannelKey, handler: String, enabled: Boolean)
  final case class ChannelPrefs(channelKey: ChannelKey, autoJoin: Boolean)
  final case class Quote(id: Long, key: String, sourceUri: String, txt: String, author: Option[String])
}
