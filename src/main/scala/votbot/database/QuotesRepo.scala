package votbot.database

import slick.jdbc.GetResult
import votbot.interop.ZioSlick
import votbot.model.DB.Quote
import votbot.model.DBError
import QuoteTable.Quotes
import zio.ZIO

trait QuotesRepo {
  val quotesRepo: QuotesRepo.Service[DatabaseProvider]
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

trait TestQuotesRepo extends QuotesRepo {
  import slick.jdbc.SQLiteProfile.api._
  private val quotes = TableQuery[Quotes]

  implicit val getQuotesResult: GetResult[Quote] =
    GetResult(r => Quote(r.nextLong(), r.nextString(), r.nextString(), r.nextString(), r.nextStringOption()))

  override val quotesRepo: QuotesRepo.Service[DatabaseProvider] = new QuotesRepo.Service[DatabaseProvider] {

    override def findRandom(): ZIO[DatabaseProvider, DBError, Option[Quote]] = {
      val q = sql"""SELECT * FROM QUOTES WHERE id IN (SELECT id FROM QUOTES ORDER BY RANDOM() LIMIT 1)"""
        .as[Quote]
        .headOption
      ZioSlick(q).refineOrDie(e => DBError("Failed to get random quote", e))
    }

    override def createSchemaIfNotExists: ZIO[DatabaseProvider, DBError, Unit] = {
      val q = quotes.schema.createIfNotExists
      ZioSlick(q).refineOrDie(e => DBError("Quotes schema create error: " + e.getMessage, e))
    }

    override def findQuotes(key: String): ZIO[DatabaseProvider, DBError, List[Quote]] = {
      val q = quotes.filter(_.key === key)
      ZioSlick(q.result).map(_.toList).refineOrDie(e => DBError("Quotes find error:" + e.getMessage, e))
    }

    override def addQuote(quote: Quote): ZIO[DatabaseProvider, DBError, Long] = {
      val q = quotes.returning(quotes.map(_.id)) += quote
      ZioSlick(q).refineOrDie(e => DBError("Error insert quote: " + e.getMessage, e))
    }

    override def addQuotes(qs: List[Quote]): ZIO[DatabaseProvider, DBError, Option[Int]] = {
      val q = quotes ++= qs
      ZioSlick(q).refineOrDie(e => DBError("Error insert quotes: " + e.getMessage, e))
    }

    override def cleanQuotes(): ZIO[DatabaseProvider, DBError, Int] =
      ZioSlick(quotes.delete).refineOrDie(e => DBError("Error clean quotes: " + e.getMessage, e))

    override def findRandomByKey(key: String): ZIO[DatabaseProvider, DBError, Option[Quote]] = {

      val q =
        sql"""SELECT * FROM QUOTES WHERE id IN (SELECT id FROM QUOTES WHERE key = $key ORDER BY RANDOM() LIMIT 1)"""
          .as[Quote]
          .headOption
      ZioSlick(q).refineOrDie(e => DBError("Error get random quote by key: " + e.getMessage, e))
    }
  }
}
