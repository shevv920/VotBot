package votbot.event

import votbot.Main.VotbotEnv
import votbot.event.Event.Event
import zio.ZIO

trait EventHandler[+E <: Event] {

  type HandlerEnv = VotbotEnv
  def handle[E1 >: E](event: E1): ZIO[HandlerEnv, Throwable, Unit]
}
