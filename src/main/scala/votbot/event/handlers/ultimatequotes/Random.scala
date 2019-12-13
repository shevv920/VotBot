package votbot.event.handlers.ultimatequotes
import votbot.Main.VotbotEnv
import votbot.database.QuotesRepo
import zio.ZIO

import scala.util.matching.Regex

object Random extends SubCommand {
  override val cmdRegex: Regex     = """(?i)r""".r
  override val description: String = "Random quote"

  override def action(subCmd: String, isAdmin: Boolean): ZIO[VotbotEnv, Throwable, Option[String]] = subCmd match {
    case cmdRegex(_) =>
      for {
        db <- ZIO.access[QuotesRepo](_.quotesRepo)
        qs <- db.findRandom()
      } yield qs.map(_.txt)
  }
}
