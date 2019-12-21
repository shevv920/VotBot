package votbot

case class Config(debug: Boolean, server: Server, bot: BotProps, admin: Admin, http: Http)
case class Server(address: String, port: Int, capRequire: Option[List[String]])
case class Admin(account: String)
case class Http(quickRequestTimeout: Int)

case class BotProps(
  nick: String,
  userName: String,
  realName: String,
  autoJoinChannels: List[String],
  versionResponse: String
)

trait Configuration {
  val configuration: Configuration.Service[Any]
}

object Configuration {

  trait Service[R] {
    val config: Config
    val http: Http     = config.http
    val server: Server = config.server
    val admin: Admin   = config.admin
    val bot: BotProps  = config.bot
  }
}

trait TestConfiguration extends Configuration {

  override val configuration = new Configuration.Service[Any] {

    val config = Config(
      debug = true,
      Server("irc.freenode.net", 6667, None),
      BotProps("votbot", "uname", "realName", List("#votbot"), "VOTBOT"),
      Admin("norm_nick"),
      Http(7)
    )
  }
}
