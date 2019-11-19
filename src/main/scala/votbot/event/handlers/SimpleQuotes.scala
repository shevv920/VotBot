package votbot.event.handlers
import votbot.Api
import votbot.event.Event.ChannelMessage
import votbot.event.{ Event, EventHandler }
import zio.ZIO
import zio.blocking.Blocking
import zio.nio.file.{ Files, Path }
import zio.random.Random

object SimpleQuotes extends EventHandler {
  override def handle(event: Event.Event): ZIO[Api with Blocking with Random, Throwable, Unit] =
    for {
      api    <- ZIO.environment[Api]
      random <- ZIO.access[Random](_.random)
      _ <- ZIO.whenCase(event) {
            case ChannelMessage(_, channel, msg) if msg.equalsIgnoreCase("!нука") =>
              for {
                lines <- Files.readAllLines(Path("src/main/resources/quotes.txt")).map(_.toVector)
                rnd   <- random.nextInt(lines.size)
                quote <- ZIO.effect(lines(rnd))
                _     <- api.sendChannelMessage(channel, quote)
              } yield ()
          }
    } yield ()
}
