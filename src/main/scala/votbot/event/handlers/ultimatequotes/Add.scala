package votbot.event.handlers.ultimatequotes

import org.jsoup.Jsoup
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
import scala.jdk.CollectionConverters._

object Add extends SubCommand {
  override val cmdRegex: Regex = """(?i)add uri=(.*)\sselector=(.*) key=(\w{1,12})""".r

  override val description: String =
    "Добавить цитаты из источника URI используя jsoup css selector и ключем KEY: add uri=URI selector=SELECTOR key=KEY"

  override def action(subCmd: String, isAdmin: Boolean): ZIO[HandlerEnv, Throwable, Option[String]] = subCmd match {
    case cmdRegex(uriStr, selector, key) if isAdmin =>
      for {
        uri <- ZIO
                .fromEither(Uri.parse(uriStr))
                .mapError(e => EventHandlerError("URI parse error: " + uriStr, UltimateQuotes))
        reg <- ZIO
                .fromTry(Try(selector.r))
                .mapError(e =>
                  EventHandlerError("Regex compile error: " + e.getMessage + " regex: " + selector, UltimateQuotes)
                )
        httpClient <- ZIO.access[HttpClient](_.httpClient)
        db         <- ZIO.access[QuotesRepo](_.quotesRepo)
        uriContent <- httpClient.quick(uri)
        uriBody    = uriContent.body
        parsed     <- ZIO.effect(Jsoup.parse(uriBody))
        matches    <- ZIO.effect(parsed.select(selector)).map(_.asScala)
        _          <- putStrLn("Found: " + matches.size)
        qs <- ZIO.foreach(matches) { m =>
               ZIO.effect(Quote(0, key, uriStr, m.text(), None))
             }
        _        <- db.addQuotes(qs)
        response = if (matches.nonEmpty) Some("added quotes: " + matches.size.toString) else None
      } yield response
    case cmdRegex(_, _, _) if !isAdmin =>
      ZIO.succeed(Some("Admin rights required"))
    case _ => ZIO.succeed(None)
  }
}
