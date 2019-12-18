package votbot.interop

import slick.dbio.DBIO
import votbot.database.DatabaseProvider
import zio.ZIO

object ZioSlick {

  def apply[T](action: DBIO[T]): ZIO[DatabaseProvider, Throwable, T] =
    for {
      db  <- ZIO.accessM[DatabaseProvider](_.databaseProvider.db)
      res <- ZIO.fromFuture(implicit ec => db.run(action))
    } yield res

}
