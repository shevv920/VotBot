package votbot.database

import slick.jdbc.SQLiteProfile.api._
import votbot.model.DB.ChannelSetting
import votbot.model.irc.ChannelKey

object ChannelSettingsTable {

  class ChannelSettings(tag: Tag) extends Table[ChannelSetting](tag, "channel_settings") {
    def channelKey = column[ChannelKey]("channel_key", O.PrimaryKey)
    def autoJoin   = column[Boolean]("auto_join")
    def *          = (channelKey, autoJoin) <> (ChannelSetting.tupled, ChannelSetting.unapply)
  }
}
