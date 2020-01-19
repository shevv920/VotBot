package votbot

import votbot.command.Command
import votbot.command.Command.{ JoinChannel, LeaveChannel, SayChannel, SayPrivate }
import zio.ZIO
import zio.console._

object ConsoleControl {

  def apply(): ZIO[Console with Api, Throwable, Unit] =
    for {
      api <- ZIO.access[Api](_.api)
      str <- getStrLn
      cmd <- Command.fromString(str)
      _ <- ZIO.whenCase(cmd) {
            case LeaveChannel(channel, reason) =>
              api.leaveChannel(channel, reason)
            case JoinChannel(channel) =>
              api.joinChannel(channel)
            case SayChannel(channel, msg) =>
              api.sendChannelMessage(channel, msg)
            case SayPrivate(target, msg) =>
              api.sendPrivateMessage(target.value, msg)
          }
      _ <- apply()
    } yield ()
}
