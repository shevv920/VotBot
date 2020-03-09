package votbot.model.irc

import enumeratum.EnumEntry
import enumeratum.Enum

sealed abstract class ChannelMode(override val entryName: String) extends EnumEntry

/* Freenode supports CFILMPQSbcefgijklmnopqrstuvz 31.01.2020 */
object ChannelMode extends Enum[ChannelMode] {
  final case class Voice(user: UserKey)  extends ChannelMode("v")
  final case class HalfOp(user: UserKey) extends ChannelMode("h")
  final case class Op(user: UserKey)     extends ChannelMode("o")
  final case class a(user: UserKey)      extends ChannelMode("a")
  final case class q(user: UserKey)      extends ChannelMode("q")

  /**Prevent users from joining or speaking. Sending /mode #channel +b alone will return the current ban list. While on the channel, banned users will be unable to send to the channel or change nick.
The most common form for a ban is +b nick!user@host. The wildcards * and ? are allowed, matching zero-or-more and exactly-one characters, respectively. Bans set on IP addresses will apply even if the affected user joins with a resolved or cloaked hostname. CIDR notation is supported in bans.
The second form can be used for bans based on user data. You can append $#channel to any ban to redirect banned users to another channel.  */
  final case class Ban(mask: String) extends ChannelMode("b")

  /**Takes one parameter, just like ban (above). Wildcards and extbans can be used, like ban.
   * Ban exemption matches override +b and +q bans for all clients it matches, allowing the exempted user to join/speak as if they were not banned or quieted.
   * This can be useful if it is necessary to ban an entire ISP due to persistent abuse, but some users from that ISP should still be allowed in.
   * For example: <br>
   * /mode #channel +bee *!*@*.example.com *!*someuser@host3.example.com $a:JohnDoe would block all users from example.com, while still allowing someuser from host3 and JohnDoe to join.*/
  final case class BanExemption(mask: String) extends ChannelMode("e")

  /** Takes a ban parameter. Extbans are supported and common, e.g.
   * for setting an exemption for a specific registered user.
   * Matching clients do not need to be invited to join the channel when it is invite-only (+i).
   * Unlike the /invite command, this does not override +j, +l and +r.
   * Only channel operators can see +I changes or see the list with /mode #channel +I. */
  final case class InviteExemption(mask: String) extends ChannelMode("I")

  /**Blocks CTCP commands (other than /me actions).*/
  final case object BlockCTCP extends ChannelMode("C")

  final case class L(target: ChannelKey) extends ChannelMode("L")

  final case object M extends ChannelMode("M")

  /** Channel does not disappear when empty. */
  final case object Permanent extends ChannelMode("P")

  /** Users cannot be forwarded (see +f above) to a channel with +Q. */
  final case object BlockForwardedUsers extends ChannelMode("Q")

  /** Only users connected via SSL may join the channel while this mode is set. Users already in the channel are not affected.
   * Keep in mind that this also blocks all webchat users, as they are not marked as connected via SSL. */
  final case object SSLOnly extends ChannelMode("S")

  /**Strip colour and formatting codes from channel messages.*/
  final case object ColorFilter extends ChannelMode("c")

  /**
   * Takes a channel name as a parameter.
   * Users who cannot join the channel (because of +i, +j, +r, see below) are instead sent to the given channel.
   * Clients are notified when the forward takes effect.
   * An operator can set mode +f #channel2 only if they are an op in #channel2 or if #channel2 has mode +F set (see below).
   * Usually you want to set forwards with MLOCK, because the channel will become empty over time and the channel modes are lost.
   * You might also want to set GUARD to prevent the channel from becoming empty.
   * An operator can use MLOCK with +f only if they have access flag +s in both channels,
   * or if the channel to be forwarded to is +F and they have +s in the original channel.
   * */
  final case object Forward extends ChannelMode("f")

  /** Allow operators in other channels to forward clients to this channel, without requiring ops in the target channel. */
  final case object EnableInForwarding extends ChannelMode("F")

  /** Users are unable to join invite-only channels unless they are invited or match a +I entry. */
  final case object InviteOnly extends ChannelMode("i")

  /** To enter the channel, you must specify the password on your /join command.
   * Keep in mind that modes locked with ChanServ's MLOCK command can be seen by anyone recreating the channel; this includes keys.
   * Also keep in mind that users being on the channel when +k is set will see the key as well. */
  final case class Password(key: String) extends ChannelMode("k")

  /** Takes a positive integer parameter. Limits the number of users who can be in the channel at the same time. */
  final case class JoinLimit(limit: Int) extends ChannelMode("l")

  /** Only opped and voiced users can send to the channel. This mode does not prevent users from changing nicks. */
  final case object Moderated extends ChannelMode("m")

  /** Users outside the channel may not send messages to it. Keep in mind that bans and quiets will not apply to external users. */
  final case object PreventExternalSend extends ChannelMode("n")

  /** The KNOCK command cannot be used on the channel, and users will not be shown the channel in whois output unless they share the channel with the requestor.
   * The channel will still appear in channel lists and WHO output (set channel mode +s if this is not desired). */
  final case object Private extends ChannelMode("p")

  /** Prevents users who are not identified to services from joining the channel. */
  final case object BlockUnidentified extends ChannelMode("r")

  /** This channel will not appear on channel lists or WHO or WHOIS output unless you are on it. */
  final case object Secret extends ChannelMode("s")

  /** Only channel operators may set the channel topic. */
  final case object OpsTopic extends ChannelMode("t")

  final case object ReducedModeration extends ChannelMode("z")

  /** Anybody in the channel may invite others (using the /invite command) to the channel. */
  final case object FreeInvite extends ChannelMode("g")

  /** This mode takes one parameter of the form n:t, where n and t are positive integers.
   * Only n users may join in each period of t seconds, so with e.g. 3:10 only 3 users could join within 10 seconds.
   * Invited users can join regardless of +j, but are counted as normal. You can use this mode to prevent bot attacks.
   * Observe the average join rate of your channel and pick a good value for +j.
   * This mode could be combined with +f to forward throttled users to an overflow channel. */
  final case object JoinThrottle extends ChannelMode("j")

  /** Works like +b (ban user), but allows matching users to join the channel. */
  final case object Quiet extends ChannelMode("q")

  /** Receive messages that are filtered server side by freenode based on content, usually spam.
   * Set +u if you want the channel to receive these messages. Also see the corresponding user mode. */
  final case object Unfiltered extends ChannelMode("u")

  val values = findValues
}
