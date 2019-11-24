package votbot

case class Config(debug: Boolean, server: Server, bot: BotProps)
case class Server(address: String, port: Int)
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
  val server   = Server("irc.freenode.net", 6667)
  val botProps = BotProps("votbot", "uname", "realName", List("#votbot"), "VOTBOT")
  val config   = Config(debug = true, server, botProps)
}
