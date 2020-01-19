package votbot.database

import slick.jdbc.GetResult
import votbot.interop.SlickToZio
import votbot.model.DB.Quote
import votbot.model.DBError
import QuoteTable.Quotes
import slick.basic.BasicBackend
import zio.{ Task, ZIO }

trait QuotesRepo {
  def findRandom(): ZIO[Any, DBError, Option[Quote]]
  def createSchemaIfNotExists: ZIO[Any, DBError, Unit]
  def findRandomByKey(key: String): ZIO[Any, DBError, Option[Quote]]
  def findQuotes(key: String): ZIO[Any, DBError, List[Quote]]
  def addQuote(quote: Quote): ZIO[Any, DBError, Long]
  def addQuotes(quotes: List[Quote]): ZIO[Any, DBError, Option[Int]]
  def cleanQuotes(): ZIO[Any, DBError, Int]
}

class SQLiteQuotesRepo(implicit val dbTask: Task[BasicBackend#DatabaseDef]) extends QuotesRepo {

  import slick.jdbc.SQLiteProfile.api._
  private val quotes = TableQuery[Quotes]

  implicit val getQuotesResult: GetResult[Quote] =
    GetResult(r => Quote(r.nextLong(), r.nextString(), r.nextString(), r.nextString(), r.nextStringOption()))

  override def findRandom(): ZIO[Any, DBError, Option[Quote]] = {
    val q = sql"""SELECT * FROM QUOTES WHERE id IN (SELECT id FROM QUOTES ORDER BY RANDOM() LIMIT 1)"""
      .as[Quote]
      .headOption
    SlickToZio(q)
      .refineOrDie(e => DBError("Failed to get random quote", e))
  }

  override def createSchemaIfNotExists: ZIO[Any, DBError, Unit] = {
    val q = quotes.schema.createIfNotExists
    SlickToZio(q)
      .refineOrDie(e => DBError("Quotes schema create error: " + e.getMessage, e))
  }

  override def findQuotes(key: String): ZIO[Any, DBError, List[Quote]] = {
    val q = quotes.filter(_.key === key)
    SlickToZio(q.result)
      .map(_.toList)
      .refineOrDie(e => DBError("Quotes find error:" + e.getMessage, e))
  }

  //fixme: deal with duplicates
  override def addQuote(quote: Quote): ZIO[Any, DBError, Long] = {
    val q = quotes.returning(quotes.map(_.id)) += quote
    SlickToZio(q)
      .refineOrDie(e => DBError("Error insert quote: " + e.getMessage, e))
  }

  override def addQuotes(qs: List[Quote]): ZIO[Any, DBError, Option[Int]] = {
    val q = quotes ++= qs
    SlickToZio(q)
      .refineOrDie(e => DBError("Error insert quotes: " + e.getMessage, e))
  }

  override def cleanQuotes(): ZIO[Any, DBError, Int] =
    SlickToZio(quotes.delete)
      .refineOrDie(e => DBError("Error clean quotes: " + e.getMessage, e))

  override def findRandomByKey(key: String): ZIO[Any, DBError, Option[Quote]] = {
    val q =
      sql"""SELECT * FROM QUOTES WHERE id IN (SELECT id FROM QUOTES WHERE key = $key ORDER BY RANDOM() LIMIT 1)"""
        .as[Quote]
        .headOption
    SlickToZio(q)
      .refineOrDie(e => DBError("Error get random quote by key: " + e.getMessage, e))
  }
}
