package votbot.event

import votbot.Main.VotbotEnv
import votbot.event.Event.Event
import zio.ZIO

trait EventHandler {

  type HandlerEnv = VotbotEnv
  def handle(event: Event): ZIO[HandlerEnv, Throwable, Unit]
}
