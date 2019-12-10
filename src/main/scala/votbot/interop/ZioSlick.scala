package votbot.interop

import slick.dbio.DBIO
import votbot.database.DatabaseProvider
import zio.ZIO

object ZioSlick {

  type ZIOSlick[T] = ZIO[DatabaseProvider, Throwable, T]

  def apply[T](action: DBIO[T]): ZIOSlick[T] =
    for {
      db  <- ZIO.accessM[DatabaseProvider](_.databaseProvider.db)
      res <- ZIO.fromFuture(implicit ec => db.run(action))
    } yield res

  implicit def DBIO_to_ZIOSLICK[T](dbio: DBIO[T]): ZIOSlick[T] = ZioSlick[T](dbio)
}
