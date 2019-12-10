package votbot.database

import slick.basic.BasicBackend
import slick.jdbc.SQLiteProfile
import zio.{ Task, ZIO }

trait DatabaseProvider {
  val databaseProvider: DatabaseProvider.Service[Any]
}

object DatabaseProvider {

  trait Service[R] {
    def db: Task[BasicBackend#DatabaseDef]
  }
}

trait TestDatabase extends DatabaseProvider {

  override val databaseProvider: DatabaseProvider.Service[Any] = new DatabaseProvider.Service[Any] {

    override def db: Task[BasicBackend#DatabaseDef] =
      ZIO.effect(SQLiteProfile.api.Database.forURL(url = "jdbc:sqlite:test.db"))
  }
}
