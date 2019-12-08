package votbot.event.handlers.ultimatequotes
import votbot.{ Api, Database }
import votbot.event.handlers.ultimatequotes.UltimateQuotes.HandlerEnv
import zio.ZIO

import scala.util.matching.Regex
import zio.console.putStrLn

object Get extends SubCommand {
  override val cmdRegex: Regex = """(?i)get (\w{1,12})""".r

  override def action(s: String): ZIO[HandlerEnv, Throwable, Option[String]] = s match {
    case cmdRegex(key) =>
      for {
        db <- ZIO.access[Database](_.database)
        qs <- db.getRandomByKey(key)
        _  <- putStrLn("UQ get command result: " + qs)
      } yield qs.map(_.txt)
    case _ =>
      ZIO.succeed(None)
  }
}
