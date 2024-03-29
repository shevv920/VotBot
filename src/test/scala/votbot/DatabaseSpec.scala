package votbot

import votbot.database.Database.Database
import votbot.model.DB.Quote
import votbot.model.irc.ChannelKey
import zio.ZIO
import zio.test.Assertion._
import zio.test._

object DatabaseSpec {

  val tests = suite("Database")(
    suite("Quotes")(
      test("create schema if not exists") {
        for {
          db <- ZIO.serviceWith[Database](_.quotesRepo)
          s  <- db.createSchemaIfNotExists
        } yield assert(s)(isUnit)
      },
      test("add and get quotes") {
        for {
          db <- ZIO.serviceWith[Database](_.quotesRepo)
          _  <- db.cleanQuotes()
          q  = Quote(0, "LENIN", "some.url.su", "bla bla bla", Some("lenin"))
          q2 = Quote(1, "LENIN", "some.url.su", "bla bla bla2", Some("lenin"))
          n  <- db.addQuotes(List(q, q2))
        } yield assert(n)(isSome(equalTo(2)))
      },
      test("get random quote by key") {
        for {
          db <- ZIO.serviceWith[Database](_.quotesRepo)
          _  <- db.cleanQuotes()
          q  = Quote(2, "STALIN", "some.url.su", "bla basla bla", Some("STALIN"))
          q2 = Quote(3, "STALIN", "some.url.su", "bla bladas bla2", Some("STALIN"))
          _  <- db.addQuotes(List(q, q2))
          q  <- db.findRandomByKey("STALIN")
        } yield assert(q.nonEmpty)(isTrue)
      }
    ),
    suite("ChannelSettings")(
      test("create schema if not exists") {
        for {
          db         <- ZIO.serviceWith[Database](_.channelSettingsRepo)
          _          <- db.createSchemaIfNotExists
          mbSettings <- db.findByKey(ChannelKey("12345"))
        } yield assert(mbSettings)(isNone)
      },
      test("find settings") {
        for {
          db         <- ZIO.serviceWith[Database](_.channelSettingsRepo)
          mbSettings <- db.findByKey(ChannelKey("1235"))
        } yield assert(mbSettings)(isNone)
      }
    )
  )
}
