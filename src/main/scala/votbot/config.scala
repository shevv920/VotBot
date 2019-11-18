package votbot

import votbot.config.Config

object config {

  case class Config(debug: Boolean, server: Server, bot: BotProps)
  case class Server(address: String, port: Int)
  case class BotProps(
    nick: String,
    userName: String,
    realName: String,
    autoJoinChannels: List[String],
    versionResponse: String
  )

}

trait Configuration extends Serializable {
  val config: Config
}
