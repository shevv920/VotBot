package votbot.model.irc

import enumeratum.EnumEntry.UpperWords
import enumeratum.{ Enum, EnumEntry }
sealed trait Command extends EnumEntry with UpperWords

object Command extends Enum[Command] {
  val values = findValues

  final case object Account extends Command
  final case object Join    extends Command
  final case object Part    extends Command
  final case object Pass    extends Command
  final case object Nick    extends Command
  final case object User    extends Command
  final case object Notice  extends Command
  final case object Ping    extends Command
  final case object Pong    extends Command
  final case object Privmsg extends Command
  final case object Kick    extends Command
  final case object Quit    extends Command
  final case object CapLs   extends Command
  final case object CapReq  extends Command
  final case object CapEnd  extends Command
  final case object Cap     extends Command

  final case class Unknown(raw: String) extends Command
  final case class Numeric(cmd: String) extends Command
}
