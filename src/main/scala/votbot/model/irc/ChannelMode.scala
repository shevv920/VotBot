package votbot.model.irc

import enumeratum.EnumEntry
import enumeratum.Enum

sealed trait ChannelMode extends EnumEntry

/* Freenode supports CFILMPQSbcefgijklmnopqrstuvz 31.01.2020 */
object ChannelMode extends Enum[ChannelMode] {
  final case class v(user: UserKey)      extends ChannelMode
  final case class h(user: UserKey)      extends ChannelMode
  final case class o(user: UserKey)      extends ChannelMode
  final case class a(user: UserKey)      extends ChannelMode
  final case class q(user: UserKey)      extends ChannelMode
  final case class b(mask: String)       extends ChannelMode
  final case class e(mask: String)       extends ChannelMode
  final case class I(mask: String)       extends ChannelMode
  final case object C                    extends ChannelMode
  final case class L(target: ChannelKey) extends ChannelMode
  final case object M                    extends ChannelMode
  final case object P                    extends ChannelMode
  final case object Q                    extends ChannelMode
  final case object S                    extends ChannelMode
  final case object c                    extends ChannelMode
  final case object f                    extends ChannelMode
  final case object i                    extends ChannelMode
  final case class k(key: String)        extends ChannelMode
  final case class l(limit: Int)         extends ChannelMode
  final case object m                    extends ChannelMode
  final case object n                    extends ChannelMode
  final case object p                    extends ChannelMode
  final case object r                    extends ChannelMode
  final case object s                    extends ChannelMode
  final case object t                    extends ChannelMode
  final case object z                    extends ChannelMode

  val values = findValues
  //todo modes to implement: F g j u
}
