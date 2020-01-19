package votbot.database

import slick.basic.BasicBackend
import zio.Task

trait Database {
  val database: Database.Service[Any]
}

object Database {

  trait Service[R] {
    def databaseProvider: DatabaseProvider.Service[R]
    def channelSettingsRepo: ChannelSettingsRepo
    def channelHandlersRepo: ChannelHandlersRepo
    def quotesRepo: QuotesRepo
  }
}

class DefaultDatabase extends Database.Service[Any] {
  override val databaseProvider: DatabaseProvider.Service[Any] = new SQLiteDatabaseProvider
  implicit val dbTask: Task[BasicBackend#DatabaseDef]          = databaseProvider.db

  override val channelHandlersRepo: ChannelHandlersRepo = new SQLiteChannelHandlersRepo
  override val channelSettingsRepo: ChannelSettingsRepo = new SQLiteChannelSettingsRepo
  override val quotesRepo: QuotesRepo                   = new SQLiteQuotesRepo
}
