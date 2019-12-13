package votbot

import votbot.model.irc.ChannelKey
import zio.ZIO
import zio.console._

object ConsoleControl {

  def parse(): ZIO[Console with Api, Throwable, Unit] =
    for {
      api <- ZIO.access[Api](_.api)
      cmd <- getStrLn.map(_.split("\\s")).map(_.toList)
      _ <- ZIO.whenCase(cmd) {
            case "leave" :: value :: _ =>
              putStrLn(s"got leave $value command") *> api.leaveChannel(ChannelKey(value))
            case "join" :: value :: _ =>
              putStrLn(s"got join $value command") *> api.joinChannel(value)
          }
      _ <- parse()
    } yield ()
}
