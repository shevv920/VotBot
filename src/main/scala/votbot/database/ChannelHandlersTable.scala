package votbot.database
import slick.jdbc.SQLiteProfile.api._
import votbot.model.DB.ChannelHandler
import votbot.model.irc.ChannelKey

object ChannelHandlersTable {

  class ChannelHandlers(tag: Tag) extends Table[ChannelHandler](tag, "channel_handlers") {
    def channelKey = column[ChannelKey]("channel_key", O.PrimaryKey)
    def handler    = column[String]("handler")
    def enabled    = column[Boolean]("enabled")
    def *          = (channelKey, handler, enabled) <> (ChannelHandler.tupled, ChannelHandler.unapply)
  }
}
