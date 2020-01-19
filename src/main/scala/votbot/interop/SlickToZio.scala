package votbot.interop

import slick.basic.BasicBackend
import slick.dbio.DBIO
import zio.{ Task, ZIO }

object SlickToZio {

  def apply[T](action: DBIO[T])(implicit dbTask: Task[BasicBackend#DatabaseDef]): Task[T] =
    for {
      db  <- dbTask
      res <- ZIO.fromFuture(implicit ec => db.run(action))
    } yield res
}
