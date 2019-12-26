package votbot.event.handlers

import votbot.Main.VotbotEnv
import votbot.event.Event.Event
import votbot.event.EventHandler
import zio.{ Ref, UIO, ZIO }

trait CustomEventHandlers {
  val customEventHandlers: CustomEventHandlers.Service[Any]
  val handlers: Ref[Set[EventHandler[Event]]]
}

object CustomEventHandlers {

  trait Service[R] {

    def registerHandler[E <: Event](handler: EventHandler[E]): UIO[Unit]
    def unregisterHandler[E <: Event](handler: EventHandler[E]): UIO[Unit]
    def handle(event: Event): ZIO[VotbotEnv, Throwable, Unit]
  }
}

trait DefaultCustomHandlers extends CustomEventHandlers {

  override val customEventHandlers: CustomEventHandlers.Service[Any] = new CustomEventHandlers.Service[Any] {

    override def registerHandler[E <: Event](handler: EventHandler[E]): UIO[Unit] =
      handlers.update(hs => hs + handler).unit

    override def unregisterHandler[E <: Event](handler: EventHandler[E]): UIO[Unit] =
      handlers.update(hs => hs.excl(handler)).unit

    override def handle(event: Event): ZIO[VotbotEnv, Throwable, Unit] =
      handlers.get.flatMap(hs => ZIO.foreach(hs)(_.handle(event))).unit
  }
}
