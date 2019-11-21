package votbot.event.handlers
import votbot.BotState
import votbot.Main.VotbotEnv
import votbot.event.Event.{ ChannelMessage, Event }
import votbot.event.EventHandler
import zio.ZIO

import scala.util.matching.Regex

trait CommandHandler extends EventHandler {
  val commands: List[String]
  val description: String
  def helpMessage: String = "Commands " + commands.mkString("[", ", ", "]") + " - " + description

  def response(channel: String): ZIO[VotbotEnv, Throwable, Unit]

  private def mkRegex: ZIO[VotbotEnv, Throwable, Regex] =
    for {
      botNick  <- ZIO.accessM[BotState](_.state.get).map(_.nick)
      regexStr <- ZIO.effect("(?i)" + botNick + ".{1,2}" + commands.mkString("|") + ".*")
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
