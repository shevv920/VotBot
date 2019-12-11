package votbot.event.handlers.ultimatequotes
import votbot.database.{ DatabaseProvider, QuotesRepo }
import votbot.event.handlers.ultimatequotes.UltimateQuotes.HandlerEnv
import zio.ZIO
import zio.console.putStrLn

import scala.util.matching.Regex

object Get extends SubCommand {
  override val cmdRegex: Regex     = """(?i)get (\w{1,12})""".r
  override val description: String = "Найти цитату по ключу : get KEY"

  override def action(subCmd: String, isAdmin: Boolean): ZIO[HandlerEnv, Throwable, Option[String]] = subCmd match {
    case cmdRegex(key) =>
      for {
        db <- ZIO.access[QuotesRepo](_.quotesRepo)
        qs <- db.getRandomByKey(key)
      } yield qs.map(_.txt)
    case _ =>
      ZIO.succeed(None)
  }
}
