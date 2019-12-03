package votbot.model.irc
import enumeratum.EnumEntry.{ Hyphencase, Lowercase }
import enumeratum.{ Enum, EnumEntry }

sealed trait Capability extends EnumEntry with Hyphencase with Lowercase

object Capabilities extends Enum[Capability] {
  val values = findValues

  final case object IdentifyMsg   extends Capability
  final case object AccountNotify extends Capability
}
