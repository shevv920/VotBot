package votbot

import votbot.Api.Api
import votbot.command.Command
import zio.Console.readLine
import zio.{Console, ZIO}

object ConsoleControl {

  def apply(): ZIO[Console with Api, Throwable, Unit] =
    for {
      str <- readLine
      cmd <- Command.fromString(str)
      _   <- Command.run(cmd)
    } yield ()
}
