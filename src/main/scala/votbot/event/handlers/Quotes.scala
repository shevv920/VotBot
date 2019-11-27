package votbot.event.handlers
import votbot.Api
import zio.ZIO
import zio.nio.file.{Files, Path}
import zio.random.Random

object Quotes extends CommandHandler {
  override val description: String = "Случайная цитата"

  override val commands: List[String] = List("q", "й", "quote", "йгщеу")

  override def response(channel: String, cmd: String, args: String): ZIO[HandlerEnv, Throwable, Unit] =
    for {
      api    <- ZIO.access[Api](_.api)
      random <- ZIO.access[Random](_.random)
      lines  <- Files.readAllLines(Path("../quotes.txt")).map(_.toVector)
      rnd    <- random.nextInt(lines.size)
      quote  <- ZIO.effect(lines(rnd))
      _      <- api.sendChannelMessage(channel, quote)
    } yield ()
}
