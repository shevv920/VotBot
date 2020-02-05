package votbot

import votbot.command.Command
import zio.ZIO
import zio.console._

object ConsoleControl {

  def apply(): ZIO[Console with Api, Throwable, Unit] =
    for {
      str <- getStrLn
      cmd <- Command.fromString(str)
      _   <- Command.run(cmd)
    } yield ()
}
