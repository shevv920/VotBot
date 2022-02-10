package votbot.database

import slick.basic.BasicBackend
import zio.{Task, ULayer, ZLayer}

object Database {
  type Database = Database.Service

  trait Service {
    def databaseProvider: DatabaseProvider.Service
    def channelSettingsRepo: ChannelSettingsRepo
    def channelHandlersRepo: ChannelHandlersRepo
    def quotesRepo: QuotesRepo
  }

  val defaultDatabase: ULayer[Database] = ZLayer.succeed(new DefaultDatabase)
}

class DefaultDatabase extends Database.Service {
  override val databaseProvider: DatabaseProvider.Service = new SQLiteDatabaseProvider
  implicit val dbTask: Task[BasicBackend#DatabaseDef]     = databaseProvider.db

  override val channelHandlersRepo: ChannelHandlersRepo = new SQLiteChannelHandlersRepo
  override val channelSettingsRepo: ChannelSettingsRepo = new SQLiteChannelSettingsRepo
  override val quotesRepo: QuotesRepo                   = new SQLiteQuotesRepo
}
