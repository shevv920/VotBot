package votbot.event.handlers.ultimatequotes

import sttp.model.Uri
import votbot.database.QuotesRepo
import votbot.HttpClient
import votbot.event.handlers.ultimatequotes.UltimateQuotes.HandlerEnv
import votbot.model.DB.Quote
import votbot.model.EventHandlerError
import zio.ZIO
import zio.console._

import scala.util.Try
import scala.util.matching.Regex

object Add extends SubCommand {
  override val cmdRegex: Regex = """(?i)add uri=(.*)\sregex=(.*) key=(\w{1,12})""".r

  override def action(s: String): ZIO[HandlerEnv, Throwable, Option[String]] = s match {
    case cmdRegex(uriStr, regex, key) =>
      for {
        uri <- ZIO
                .fromEither(Uri.parse(uriStr))
                .mapError(e => EventHandlerError("URI parse error: " + uriStr, UltimateQuotes))
        reg <- ZIO
                .fromTry(Try(regex.r))
                .mapError(e =>
                  EventHandlerError("Regex compile error: " + e.getMessage + " regex: " + regex, UltimateQuotes)
                )
        httpClient <- ZIO.access[HttpClient](_.httpClient)
        db         <- ZIO.access[QuotesRepo](_.quotesRepo)
        uriContent <- httpClient.quick(uri)
        matches    <- ZIO.effect(reg.findAllIn(uriContent.body).toList)
        _          <- putStrLn("Found: " + matches.mkString(", "))
        qs <- ZIO.foreach(matches) { m =>
               ZIO.effect(Quote(0, key, uriStr, m, None))
             }
        _        <- db.addQuotes(qs)
        response = if (matches.nonEmpty) Some(matches.size.toString) else None
      } yield response
    case _ => ZIO.succeed(None)
  }
}
