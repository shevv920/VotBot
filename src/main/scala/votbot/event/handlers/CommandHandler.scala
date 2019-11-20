package votbot.event.handlers
import votbot.{ Api, BotState }
import votbot.Main.VotbotEnv
import votbot.event.Event.{ ChannelMessage, Event, IncomingMessage, PrivateMessage }
import votbot.event.EventHandler
import zio.ZIO
import zio.nio.file.{ Files, Path }
import zio.random.Random

import scala.util.matching.Regex

trait CommandHandler extends EventHandler {
  val commands: List[String]

  def response(channel: String): ZIO[VotbotEnv, Throwable, Unit]

  private def mkRegex: ZIO[VotbotEnv, Throwable, Regex] =
    for {
      botNick  <- ZIO.accessM[BotState](_.state.get).map(_.nick)
      regexStr <- ZIO.effect("(?i)" + botNick + ".{1,2}" + "[" + commands.mkString("|") + "]" + ".*")
      regex    <- ZIO.effect(regexStr.r)
    } yield regex

  override def handle(event: Event): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      regex <- mkRegex
      _ <- ZIO.whenCase(event) {
            case cm: ChannelMessage if regex.findFirstIn(cm.msg).nonEmpty =>
              response(cm.channel)
          }
    } yield ()
}

object Quotes extends CommandHandler {
  override val commands: List[String] = List("q", "й", "quote", "йгщеу")
  override def response(channel: String): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      api    <- ZIO.environment[Api]
      random <- ZIO.access[Random](_.random)
      lines  <- Files.readAllLines(Path("../quotes.txt")).map(_.toVector)
      rnd    <- random.nextInt(lines.size)
      quote  <- ZIO.effect(lines(rnd))
      _      <- api.sendChannelMessage(channel, quote)
    } yield ()
}
