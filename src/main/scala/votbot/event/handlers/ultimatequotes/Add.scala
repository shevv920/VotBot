package votbot.event.handlers.ultimatequotes

import org.jsoup.Jsoup
import sttp.model.Uri
import votbot.HttpClient
import votbot.database.QuotesRepo
import votbot.event.handlers.ultimatequotes.UltimateQuotes.HandlerEnv
import votbot.model.DB.Quote
import votbot.model.EventHandlerError
import zio.ZIO

import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

object Add extends SubCommand {
  override val cmdRegex: Regex = """(?i)add uri=(.*)\sselector=(.*) key=(\w{1,12})""".r

  override val description: String =
    "add quotes from uri using css selector with the key: add uri=URI selector=SELECTOR key=KEY"

  override def action(subCmd: String, isAdmin: Boolean): ZIO[HandlerEnv, Throwable, Option[String]] = subCmd match {
    case cmdRegex(uriStr, selector, key) if isAdmin =>
      for {
        uri <- ZIO
                .fromEither(Uri.parse(uriStr))
                .mapError(e => EventHandlerError("URI parse error: " + uriStr, UltimateQuotes))
        httpClient <- ZIO.access[HttpClient](_.httpClient)
        db         <- ZIO.access[QuotesRepo](_.quotesRepo)
        uriContent <- httpClient.quick(uri)
        uriBody    = uriContent.body
        parsed     <- ZIO.effect(Jsoup.parse(uriBody))
        matches    <- ZIO.effect(parsed.select(selector)).map(_.asScala)
        qs <- ZIO.foreach(matches) { m =>
               ZIO.effect(Quote(0, key.toLowerCase, uriStr, m.text(), None))
             }
        n <- db.addQuotes(qs)
      } yield n.map("added quotes: " + _.toString)
    case cmdRegex(_, _, _) if !isAdmin =>
      ZIO.succeed(Some("Admin rights required"))
    case _ => ZIO.succeed(None)
  }
}
