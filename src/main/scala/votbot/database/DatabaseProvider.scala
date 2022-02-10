package votbot.database

import slick.basic.BasicBackend
import slick.jdbc.SQLiteProfile
import zio.{Task, ULayer, ZIO, ZLayer}

object DatabaseProvider {
  type DatabaseProvider = DatabaseProvider.Service

  trait Service {
    def db: Task[BasicBackend#DatabaseDef]
  }

  val SQLiteDatabaseProvider: ULayer[DatabaseProvider] = ZLayer.succeed(new SQLiteDatabaseProvider)
}

class SQLiteDatabaseProvider extends DatabaseProvider.Service {

  override def db: Task[BasicBackend#DatabaseDef] =
    ZIO.attempt(SQLiteProfile.api.Database.forURL(url = "jdbc:sqlite:database.db"))
}
