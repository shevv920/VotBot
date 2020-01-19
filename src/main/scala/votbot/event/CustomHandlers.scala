package votbot.event

import votbot.Main.VotbotEnv
import zio.ZIO

trait CustomHandlers {
  val customHandlers: CustomHandlers.Service[Any]
}

object CustomHandlers {
  type Handle = PartialFunction[Event, ZIO[VotbotEnv, Throwable, Unit]]

  trait Service[R] {
    def register(handler: Handle): ZIO[R, Throwable, Unit]
    def unregister(handler: Handle): ZIO[R, Throwable, Unit]
    def handle(event: Event): ZIO[VotbotEnv, Throwable, Unit]
  }

  object > extends Service[CustomHandlers] {

    override def register(handler: Handle): ZIO[CustomHandlers, Throwable, Unit] =
      ZIO.accessM[CustomHandlers](_.customHandlers.register(handler))

    override def unregister(handler: Handle): ZIO[CustomHandlers, Throwable, Unit] =
      ZIO.accessM[CustomHandlers](_.customHandlers.unregister(handler))

    override def handle(event: Event): ZIO[VotbotEnv, Throwable, Unit] =
      ZIO.accessM[VotbotEnv](_.customHandlers.handle(event))
  }
}
