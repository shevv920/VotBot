package votbot

import votbot.database.QuotesRepo
import votbot.model.DB.Quote
import zio.ZIO
import zio.test.Assertion._
import zio.test._

object DatabaseSpec {

  val tests = suite("Database")(
    suite("Quotes")(
      testM("delete all rows") {
        for {
          db <- ZIO.access[QuotesRepo](_.quotesRepo)
          _  <- db.createSchemaIfNotExists
          n  <- db.cleanQuotes()
        } yield assert(n, isGreaterThan(0))
      },
      testM("add and get quotes") {
        for {
          db <- ZIO.access[QuotesRepo](_.quotesRepo)
          q  = Quote(0, "LENIN", "some.url.su", "bla bla bla", Some("lenin"))
          q2 = Quote(1, "LENIN", "some.url.su", "bla bla bla2", Some("lenin"))
          n  <- db.addQuotes(List(q, q2))
        } yield assert(n, isSome(isGreaterThan(0)))
      },
      testM("get random quote by key") {
        for {
          db <- ZIO.access[QuotesRepo](_.quotesRepo)
          q  = Quote(2, "STALIN", "some.url.su", "bla bla bla", Some("STALIN"))
          q2 = Quote(3, "STALIN", "some.url.su", "bla bla bla2", Some("STALIN"))
          _  <- db.addQuotes(List(q, q2))
          q  <- db.getRandomByKey("STALIN")
        } yield assert(q.nonEmpty, isTrue)
      }
    )
  )
}
