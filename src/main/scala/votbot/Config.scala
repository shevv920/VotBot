package votbot

case class Config(debug: Boolean, server: Server, bot: BotProps, admin: Admin)
case class Server(address: String, port: Int, capRequire: Option[List[String]])
case class Admin(account: String)

case class BotProps(
  nick: String,
  userName: String,
  realName: String,
  autoJoinChannels: List[String],
  versionResponse: String
)

trait Configuration extends Serializable {
  val config: Config
}

trait TestConfiguration extends Configuration {
  val server   = Server("irc.freenode.net", 6667, None)
  val botProps = BotProps("votbot", "uname", "realName", List("#votbot"), "VOTBOT")
  val admin    = Admin("norm_nick")
  val config   = Config(debug = true, server, botProps, admin)
}
