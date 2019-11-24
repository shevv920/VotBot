package votbot.event.handlers
import votbot.BotState
import votbot.event.Event.{ ChannelMessage, Event }
import votbot.event.EventHandler
import zio.ZIO

import scala.util.matching.Regex

trait CommandHandler extends EventHandler {
  val commands: List[String]
  val description: String
  def helpMessage: String = commands.mkString("[", ", ", "]") + " - " + description //fixme escape commands strings

  def response(channel: String, command: String, arg: String): ZIO[HandlerEnv, Throwable, Unit]

  override def handle(event: Event): ZIO[HandlerEnv, Throwable, Unit] =
    for {
      regex <- mkRegex
      _ <- ZIO.whenCase(event) {
            case ChannelMessage(sender, channel, regex(cmd, arg)) =>
              response(channel, cmd, arg)
          }
    } yield ()

  private def mkRegex: ZIO[BotState, Throwable, Regex] =
    for {
      botNick  <- ZIO.accessM[BotState](_.state.get).map(_.nick)
      regexStr <- ZIO.effect("(?i)" + botNick + ".{1,2}" + commands.mkString("(", "|", ")") + "\\s?(.*)?")
      regex    <- ZIO.effect(regexStr.r)
    } yield regex
}
