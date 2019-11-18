package votbot
import io.getquill.{ Literal, SqliteJdbcContext }

trait Database {
  val ctx: SqliteJdbcContext[Literal]

}
