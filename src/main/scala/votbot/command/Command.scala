package votbot.command

import votbot.model.irc.{ ChannelKey, UserKey }
import zio.{ UIO, ZIO }

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

  def fromString(str: String): UIO[Command] = ZIO.effectTotal {
    if (fullMatcher.isDefinedAt(str))
      fullMatcher(str)
    else
      WrongCommand(str)
  }

}
