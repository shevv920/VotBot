package votbot.command

import votbot.Api
import votbot.Api.Api
import votbot.model.irc.{ ChannelKey, UserKey }
import zio.{ UIO, ZIO }
import zio.Console
import zio.Console.printLine

sealed trait Command

object Command {
  final case class JoinChannel(channel: ChannelKey)                  extends Command
  final case class LeaveChannel(channel: ChannelKey, reason: String) extends Command
  final case class SayChannel(channel: ChannelKey, msg: String)      extends Command
  final case class SayPrivate(target: UserKey, msg: String)          extends Command
  final case class Quit(reason: String)                              extends Command
  final case class WrongCommand(raw: String)                         extends Command

  private val quitRegex       = """(?i)quit *(.*)?""".r
  private val joinRegex       = """(?i)join *(#\w+)""".r
  private val leaveRegex      = """(?i)leave *(#\w+) (.*)""".r
  private val sayChannelRegex = """(?i)say *(#\w+) +(.*)""".r
  private val sayPrivateRegex = """(?i)say *(\w+) +(.*)""".r

  type Matcher = PartialFunction[String, Command]

  val quitMatcher: Matcher = {
    case quitRegex(reason) => Quit(reason)
  }

  val joinMatcher: Matcher = {
    case joinRegex(channel) => JoinChannel(ChannelKey(channel))
  }

  val leaveMatcher: Matcher = {
    case leaveRegex(channel, reason) => LeaveChannel(ChannelKey(channel), reason)
  }

  val sayChannelMatcher: Matcher = {
    case sayChannelRegex(channel, msg) => SayChannel(ChannelKey(channel), msg)
  }

  val sayPrivateMatcher: Matcher = {
    case sayPrivateRegex(target, msg) => SayPrivate(UserKey(target), msg)
  }

  val matchers: Seq[Matcher] = List(quitMatcher, joinMatcher, leaveMatcher, sayChannelMatcher, sayPrivateMatcher)

  val fullMatcher: Matcher = matchers.fold(PartialFunction.empty[String, Command])(_.orElse(_))

  def fromString(str: String): UIO[Command] = ZIO.succeed {
    if (fullMatcher.isDefinedAt(str))
      fullMatcher(str)
    else
      WrongCommand(str)
  }

  def run(cmd: Command): ZIO[Console with Api, Throwable, Unit] =
    for {
      api <- ZIO.service[Api.Service]
      _ <- ZIO.whenCase(cmd) {
            case LeaveChannel(channel, reason) =>
              api.leaveChannel(channel, reason)
            case JoinChannel(channel) =>
              api.joinChannel(channel)
            case SayChannel(channel, msg) =>
              api.sendChannelMessage(channel, msg)
            case SayPrivate(target, msg) =>
              api.sendPrivateMessage(target.value, msg)
            case WrongCommand(raw) =>
              printLine(s"Unknown command $raw")
          }
    } yield ()

}
