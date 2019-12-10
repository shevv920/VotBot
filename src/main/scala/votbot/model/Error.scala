package votbot.model

import votbot.event.EventHandler

trait Error extends Throwable {
  def description: String
}
final case class StartupError(description: String)                             extends Error
final case class EventHandlerError(description: String, handler: EventHandler) extends Error
final case class DBError(description: String, error: Throwable)                extends Error
