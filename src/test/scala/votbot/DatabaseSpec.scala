package votbot
import votbot.model.DB.Quote
import zio.ZIO
import zio.test.Assertion._
import zio.test._

object DatabaseSpec {

  val tests = suite("Database")(
    suite("Quotes")(
      testM("add and get quotes") {
        for {
          db <- ZIO.access[Database](_.database)
          q  = Quote(0, "LENIN", "some.url.su", "bla bla bla", Some("lenin"))
          q2 = Quote(1, "LENIN", "some.url.su", "bla bla bla2", Some("lenin"))
          _  <- db.cleanQuotes()
          _  <- db.addQuotes(List(q, q2))
          r  <- db.findQuotes("LENIN")
        } yield assert(r, equalTo(List(q, q2)))
      }
    )
  )
}
