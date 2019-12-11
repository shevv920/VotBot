package votbot.model

import votbot.model.irc.ChannelKey

object DB {
  final case class ChannelSetting(channelKey: ChannelKey, autoJoin: Boolean)
  final case class Quote(id: Long, key: String, sourceUri: String, txt: String, author: Option[String])
}
