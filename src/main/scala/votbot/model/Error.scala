package votbot.model

import votbot.event.Event.Event
import votbot.event.EventHandler

trait Error extends Throwable {
  def description: String
}
final case class StartupError(description: String)                                    extends Error
final case class EventHandlerError(description: String, handler: EventHandler[Event]) extends Error
final case class DBError(description: String, throwable: Throwable)                   extends Error
