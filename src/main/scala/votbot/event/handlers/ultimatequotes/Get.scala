package votbot.event.handlers.ultimatequotes
import votbot.database.{ DatabaseProvider, QuotesRepo }
import votbot.event.handlers.ultimatequotes.UltimateQuotes.HandlerEnv
import zio.ZIO
import zio.console.putStrLn

import scala.util.matching.Regex

object Get extends SubCommand {
  override val cmdRegex: Regex = """(?i)get (\w{1,12})""".r

  override def action(s: String): ZIO[HandlerEnv, Throwable, Option[String]] = s match {
    case cmdRegex(key) =>
      for {
        db <- ZIO.access[QuotesRepo](_.quotesRepo)
        qs <- db.getRandomByKey(key)
        _  <- putStrLn("UQ get command result: " + qs)
      } yield qs.map(_.txt)
    case _ =>
      ZIO.succeed(None)
  }
}
