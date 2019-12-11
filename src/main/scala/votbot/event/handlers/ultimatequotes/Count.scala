package votbot.event.handlers.ultimatequotes

import votbot.database.QuotesRepo
import votbot.event.handlers.ultimatequotes.UltimateQuotes.HandlerEnv
import zio.ZIO

import scala.util.matching.Regex

object Count extends SubCommand {
  override val cmdRegex: Regex     = """count (\w{1,12})""".r
  override val description: String = "посчитать сколько цитату по ключу в базе"

  override def action(subCmd: String, isAdmin: Boolean): ZIO[HandlerEnv, Throwable, Option[String]] =
    subCmd match {
      case cmdRegex(key) =>
        for {
          db <- ZIO.access[QuotesRepo](_.quotesRepo)
          n  <- db.findQuotes(key).map(_.size)
        } yield Some(n.toString)
      case _ =>
        ZIO.succeed(None)
    }
}
