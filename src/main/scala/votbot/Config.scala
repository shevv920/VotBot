package votbot

import java.nio.file.Paths

import pureconfig.ConfigSource
import zio.{ Has, ULayer, URIO, ZIO, ZLayer, system }
import pureconfig.generic.auto._

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

object Configuration {
  type Configuration = Has[Configuration.Service]

  trait Service {
    def config: Config
    def http: Http
    def server: Server
    def admin: Admin
    def bot: BotProps
  }

  def bot: URIO[Configuration, BotProps]  = ZIO.access[Configuration](_.get.bot)
  def config: URIO[Configuration, Config] = ZIO.access[Configuration](_.get.config)
  def http: URIO[Configuration, Http]     = ZIO.access[Configuration](_.get.http)
  def server: URIO[Configuration, Server] = ZIO.access[Configuration](_.get.server)
  def admin: URIO[Configuration, Admin]   = ZIO.access[Configuration](_.get.admin)

  private def mkCfgPath() =
    system
      .property("user.dir")
      .map(_.getOrElse(".") + "/" + "application.conf")
      .map(Paths.get(_))

  val defaultConfig: ZLayer[system.System, Serializable, Configuration] =
    (for {
      path <- mkCfgPath()
      cfg <- ZIO
              .fromEither(ConfigSource.file(path).load[Config])
              .orElse(ZIO.fromEither(ConfigSource.default.load[Config]))
    } yield new Service {
      override val config: Config = cfg
      override val http: Http     = cfg.http
      override val server: Server = cfg.server
      override val admin: Admin   = cfg.admin
      override val bot: BotProps  = cfg.bot
    }).toLayer

  val testConfiguration: ULayer[Has[TestConfiguration]] = ZLayer.succeed(new TestConfiguration)
}

class TestConfiguration extends Configuration.Service {

  val config = Config(
    debug = true,
    Server("irc.freenode.net", 6667, None),
    BotProps("votbot", "uname", "realName", List("#votbot"), "VOTBOT"),
    Admin("norm_nick"),
    Http(7)
  )

  val http: Http     = config.http
  val server: Server = config.server
  val admin: Admin   = config.admin
  val bot: BotProps  = config.bot

}
