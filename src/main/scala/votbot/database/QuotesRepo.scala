package votbot.database

import slick.jdbc.GetResult
import votbot.interop.ZioSlick
import votbot.model.DB.Quote
import votbot.model.DBError
import QuoteTable.Quotes
import zio.ZIO

trait QuotesRepo {
  val quotesRepo: QuotesRepo.Service[Any]
}

object QuotesRepo {

  trait Service[R] {
    def findRandom(): ZIO[R, DBError, Option[Quote]]
    def createSchemaIfNotExists: ZIO[R, DBError, Unit]
    def findRandomByKey(key: String): ZIO[R, DBError, Option[Quote]]
    def findQuotes(key: String): ZIO[R, DBError, List[Quote]]
    def addQuote(quote: Quote): ZIO[R, DBError, Long]
    def addQuotes(quotes: List[Quote]): ZIO[R, DBError, Option[Int]]
    def cleanQuotes(): ZIO[R, DBError, Int]
  }
}

trait SqliteQuotesRepo extends QuotesRepo { self: DatabaseProvider =>

  import slick.jdbc.SQLiteProfile.api._
  private val quotes = TableQuery[Quotes]

  implicit val getQuotesResult: GetResult[Quote] =
    GetResult(r => Quote(r.nextLong(), r.nextString(), r.nextString(), r.nextString(), r.nextStringOption()))

  override val quotesRepo: QuotesRepo.Service[Any] = new QuotesRepo.Service[Any] {

    override def findRandom(): ZIO[Any, DBError, Option[Quote]] = {
      val q = sql"""SELECT * FROM QUOTES WHERE id IN (SELECT id FROM QUOTES ORDER BY RANDOM() LIMIT 1)"""
        .as[Quote]
        .headOption
      ZioSlick(q).refineOrDie(e => DBError("Failed to get random quote", e)).provide(self)
    }

    override def createSchemaIfNotExists: ZIO[Any, DBError, Unit] = {
      val q = quotes.schema.createIfNotExists
      ZioSlick(q).refineOrDie(e => DBError("Quotes schema create error: " + e.getMessage, e)).provide(self)
    }

    override def findQuotes(key: String): ZIO[Any, DBError, List[Quote]] = {
      val q = quotes.filter(_.key === key)
      ZioSlick(q.result).map(_.toList).refineOrDie(e => DBError("Quotes find error:" + e.getMessage, e)).provide(self)
    }

    override def addQuote(quote: Quote): ZIO[Any, DBError, Long] = {
      val q = quotes.returning(quotes.map(_.id)) += quote
      ZioSlick(q).refineOrDie(e => DBError("Error insert quote: " + e.getMessage, e)).provide(self)
    }

    override def addQuotes(qs: List[Quote]): ZIO[Any, DBError, Option[Int]] = {
      val q = quotes ++= qs
      ZioSlick(q).refineOrDie(e => DBError("Error insert quotes: " + e.getMessage, e)).provide(self)
    }

    override def cleanQuotes(): ZIO[Any, DBError, Int] =
      ZioSlick(quotes.delete).refineOrDie(e => DBError("Error clean quotes: " + e.getMessage, e)).provide(self)

    override def findRandomByKey(key: String): ZIO[Any, DBError, Option[Quote]] = {
      val q =
        sql"""SELECT * FROM QUOTES WHERE id IN (SELECT id FROM QUOTES WHERE key = $key ORDER BY RANDOM() LIMIT 1)"""
          .as[Quote]
          .headOption
      ZioSlick(q).refineOrDie(e => DBError("Error get random quote by key: " + e.getMessage, e)).provide(self)
    }
  }
}
