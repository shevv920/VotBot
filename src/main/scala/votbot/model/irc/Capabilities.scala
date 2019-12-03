package votbot.model.irc
import enumeratum.EnumEntry.Hyphencase
import enumeratum.{ Enum, EnumEntry }

sealed trait Capability extends EnumEntry with Hyphencase

object Capabilities extends Enum[Capability] {
  val values = findValues
  final case object IdentifyMsg extends Capability
}
