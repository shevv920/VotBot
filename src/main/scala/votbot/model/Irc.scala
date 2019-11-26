package votbot.model

import enumeratum.EnumEntry.Uppercase
import enumeratum.{Enum, EnumEntry}

import scala.language.implicitConversions

object Irc {

  object Numeric {
    val RPL_WELCOME           = "001"
    val RPL_YOURHOST          = "002"
    val RPL_CREATED           = "003"
    val RPL_MYINFO            = "004"
    val RPL_BOUNCE            = "005"
    val RPL_USERHOST          = "302"
    val RPL_ISON              = "303"
    val RPL_AWAY              = "301"
    val RPL_UNAWAY            = "305"
    val RPL_NOWAWAY           = "306"
    val RPL_WHOISUSER         = "311"
    val RPL_WHOISSERVER       = "312"
    val RPL_WHOISOPERATOR     = "313"
    val RPL_WHOISIDLE         = "317"
    val RPL_ENDOFWHOIS        = "318"
    val RPL_WHOISCHANNELS     = "319"
    val RPL_WHOWASUSER        = "314"
    val RPL_ENDOFWHOWAS       = "369"
    val RPL_LISTSTART         = "321"
    val RPL_LIST              = "322"
    val RPL_LISTEND           = "323"
    val RPL_UNIQOPIS          = "325"
    val RPL_CHANNELMODEIS     = "324"
    val RPL_NOTOPIC           = "331"
    val RPL_TOPIC             = "332"
    val RPL_INVITING          = "341"
    val RPL_SUMMONING         = "342"
    val RPL_INVITELIST        = "346"
    val RPL_ENDOFINVITELIST   = "347"
    val RPL_EXCEPTLIST        = "348"
    val RPL_ENDOFEXCEPTLIST   = "349"
    val RPL_VERSION           = "351"
    val RPL_WHOREPLY          = "352"
    val RPL_ENDOFWHO          = "315"
    val RPL_NAMREPLY          = "353"
    val RPL_ENDOFNAMES        = "366"
    val RPL_LINKS             = "364"
    val RPL_ENDOFLINKS        = "365"
    val RPL_BANLIST           = "367"
    val RPL_ENDOFBANLIST      = "368"
    val RPL_INFO              = "371"
    val RPL_ENDOFINFO         = "374"
    val RPL_MOTDSTART         = "375"
    val RPL_MOTD              = "372"
    val RPL_ENDOFMOTD         = "376"
    val RPL_YOUREOPER         = "381"
    val RPL_REHASHING         = "382"
    val RPL_YOURESERVICE      = "383"
    val RPL_TIME              = "391"
    val RPL_USERSSTART        = "392"
    val RPL_USERS             = "393"
    val RPL_ENDOFUSERS        = "394"
    val RPL_NOUSERS           = "395"
    val RPL_TRACELINK         = "200"
    val RPL_TRACECONNECTING   = "201"
    val RPL_TRACEHANDSHAKE    = "202"
    val RPL_TRACEUNKNOWN      = "203"
    val RPL_TRACEOPERATOR     = "204"
    val RPL_TRACEUSER         = "205"
    val RPL_TRACESERVER       = "206"
    val RPL_TRACESERVICE      = "207"
    val RPL_TRACENEWTYPE      = "208"
    val RPL_TRACECLASS        = "209"
    val RPL_TRACERECONNECT    = "210"
    val RPL_TRACELOG          = "261"
    val RPL_TRACEEND          = "262"
    val RPL_STATSLINKINFO     = "211"
    val RPL_STATSCOMMANDS     = "212"
    val RPL_ENDOFSTATS        = "219"
    val RPL_STATSUPTIME       = "242"
    val RPL_STATSOLINE        = "243"
    val RPL_UMODEIS           = "221"
    val RPL_SERVLIST          = "234"
    val RPL_SERVLISTEND       = "235"
    val RPL_LUSERCLIENT       = "251"
    val RPL_LUSEROP           = "252"
    val RPL_LUSERUNKNOWN      = "253"
    val RPL_LUSERCHANNELS     = "254"
    val RPL_LUSERME           = "255"
    val RPL_ADMINME           = "256"
    val RPL_ADMINLOC1         = "257"
    val RPL_ADMINLOC2         = "258"
    val RPL_ADMINEMAIL        = "259"
    val RPL_TRYAGAIN          = "263"
    val ERR_NOSUCHNICK        = "401"
    val ERR_NOSUCHSERVER      = "402"
    val ERR_NOSUCHCHANNEL     = "403"
    val ERR_CANNOTSENDTOCHAN  = "404"
    val ERR_TOOMANYCHANNELS   = "405"
    val ERR_WASNOSUCHNICK     = "406"
    val ERR_TOOMANYTARGETS    = "407"
    val ERR_NOSUCHSERVICE     = "408"
    val ERR_NOORIGIN          = "409"
    val ERR_NORECIPIENT       = "411"
    val ERR_NOTEXTTOSEND      = "412"
    val ERR_NOTOPLEVEL        = "413"
    val ERR_WILDTOPLEVEL      = "414"
    val ERR_BADMASK           = "415"
    val ERR_UNKNOWNCOMMAND    = "421"
    val ERR_NOMOTD            = "422"
    val ERR_NOADMININFO       = "423"
    val ERR_FILEERROR         = "424"
    val ERR_NONICKNAMEGIVEN   = "431"
    val ERR_ERRONEUSNICKNAME  = "432"
    val ERR_NICKNAMEINUSE     = "433"
    val ERR_NICKCOLLISION     = "436"
    val ERR_UNAVAILRESOURCE   = "437"
    val ERR_USERNOTINCHANNEL  = "441"
    val ERR_NOTONCHANNEL      = "442"
    val ERR_USERONCHANNEL     = "443"
    val ERR_NOLOGIN           = "444"
    val ERR_SUMMONDISABLED    = "445"
    val ERR_USERSDISABLED     = "446"
    val ERR_NOTREGISTERED     = "451"
    val ERR_NEEDMOREPARAMS    = "461"
    val ERR_ALREADYREGISTRED  = "462"
    val ERR_NOPERMFORHOST     = "463"
    val ERR_PASSWDMISMATCH    = "464"
    val ERR_YOUREBANNEDCREEP  = "465"
    val ERR_YOUWILLBEBANNED   = "466"
    val ERR_KEYSET            = "467"
    val ERR_CHANNELISFULL     = "471"
    val ERR_UNKNOWNMODE       = "472"
    val ERR_INVITEONLYCHAN    = "473"
    val ERR_BANNEDFROMCHAN    = "474"
    val ERR_BADCHANNELKEY     = "475"
    val ERR_BADCHANMASK       = "476"
    val ERR_NOCHANMODES       = "477"
    val ERR_BANLISTFULL       = "478"
    val ERR_NOPRIVILEGES      = "481"
    val ERR_CHANOPRIVSNEEDED  = "482"
    val ERR_CANTKILLSERVER    = "483"
    val ERR_RESTRICTED        = "484"
    val ERR_UNIQOPPRIVSNEEDED = "485"
    val ERR_NOOPERHOST        = "491"
    val ERR_UMODEUNKNOWNFLAG  = "501"
    val ERR_USERSDONTMATCH    = "502"
  }

  sealed trait Message
  final case class RawMessage(cmd: Command, args: Vector[String], prefix: Option[Prefix] = None) extends Message

  object RawMessage {

    def apply(cmd: Command, args: String*): RawMessage =
      if (args.size > 1)
        new RawMessage(cmd, args.toVector.updated(args.size - 1, ":" + args.last), None)
      else
        new RawMessage(cmd, args.toVector, None)
  }

  final case class Prefix(nick: String, username: String, host: String)

  sealed trait Command extends EnumEntry with Uppercase

  object Command extends Enum[Command] {
    val values = findValues

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

    final case class Unknown(raw: String) extends Command
    final case class Numeric(cmd: String) extends Command
  }

  case class ChannelKey(str: String) extends AnyVal
  case class UserKey(str: String)    extends AnyVal

  final case class Channel(name: String, modes: List[ChannelMode], members: Set[UserKey])
  final case class ChannelMode(mode: String, args: Option[String])
  final case class User(name: String, channels: Set[ChannelKey])
  implicit def strToChannelKey(str: String): ChannelKey = ChannelKey(str.toLowerCase)
  implicit def channelKeyToStr(key: ChannelKey): String = key.str
  implicit def userKeyToStr(key: UserKey): String       = key.str
  implicit def strToUserKey(str: String): UserKey       = UserKey(str.toLowerCase)
}
