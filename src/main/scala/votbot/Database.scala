package votbot
import io.getquill.{ Literal, SqliteJdbcContext }
import votbot.model.DB.Quote
import zio.{ Task, ZIO }

trait Database {
  val database: Database.Service[Any]
}

object Database {

  trait Service[R] {
    val ctx: SqliteJdbcContext[Literal]

    def findQuotes(key: String): Task[List[Quote]]
    def addQuote(quote: Quote): Task[Unit]
    def addQuotes(quotes: List[Quote]): Task[Unit]
    def cleanQuotes(): Task[Unit]
  }
}

trait TestDatabase extends Database {

  val database = new Database.Service[Any] {
    override val ctx: SqliteJdbcContext[Literal] = new SqliteJdbcContext(Literal, "database")
    import ctx._

    override def cleanQuotes(): Task[Unit] =
      ZIO.effect { ctx.run(quote(query[Quote].delete)) }
    override def findQuotes(key: String): Task[List[Quote]] =
      ZIO.effect { ctx.run(quote(query[Quote].filter(_.key == lift(key)))) }
    override def addQuote(q: Quote): Task[Unit] =
      ZIO.effect { ctx.run(quote(query[Quote].insert(lift(q)))) }
    override def addQuotes(qs: List[Quote]): Task[Unit] =
      ZIO
        .foreach(qs)(q => ZIO.effect(ctx.run(quote(query[Quote].insert(lift(q))))))
        .unit
  }
}
