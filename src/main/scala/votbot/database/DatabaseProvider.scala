package votbot.database

import slick.basic.BasicBackend
import slick.jdbc.SQLiteProfile
import zio.ZLayer.NoDeps
import zio.{ Has, Task, ZIO, ZLayer }

object DatabaseProvider {
  type DatabaseProvider = Has[DatabaseProvider.Service]

  trait Service {
    def db: Task[BasicBackend#DatabaseDef]
  }

  val SQLiteDatabaseProvider: NoDeps[Nothing, DatabaseProvider] = ZLayer.succeed(new SQLiteDatabaseProvider)
}

class SQLiteDatabaseProvider extends DatabaseProvider.Service {

  override def db: Task[BasicBackend#DatabaseDef] =
    ZIO.effect(SQLiteProfile.api.Database.forURL(url = "jdbc:sqlite:database.db"))
}
