package votbot.event.handlers
import votbot.Api
import votbot.event.Event.{ Event, Join }
import votbot.event.EventHandler
import zio.ZIO

object Hello extends EventHandler {
  override def handle(event: Event): ZIO[HandlerEnv, Throwable, Unit] =
    for {
      api <- ZIO.environment[Api]
      _ <- ZIO.whenCase(event) {
            case Join(user, channel) =>
              api.sendChannelMessage(channel, "Hello " + user)
          }
    } yield ()
}
