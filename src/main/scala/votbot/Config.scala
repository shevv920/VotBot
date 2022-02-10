package votbot

import java.nio.file.Paths

import pureconfig.ConfigSource
import zio.{ ULayer, URIO, ZIO, ZLayer }
import pureconfig.generic.auto._
import zio.System

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
  type Configuration = Configuration.Service

  trait Service {
    def config: Config
    def http: Http
    def server: Server
    def admin: Admin
    def bot: BotProps
  }

  def bot: URIO[Configuration, BotProps]  = ZIO.serviceWith[Configuration](_.bot)
  def config: URIO[Configuration, Config] = ZIO.serviceWith[Configuration](_.config)
  def http: URIO[Configuration, Http]     = ZIO.serviceWith[Configuration](_.http)
  def server: URIO[Configuration, Server] = ZIO.serviceWith[Configuration](_.server)
  def admin: URIO[Configuration, Admin]   = ZIO.serviceWith[Configuration](_.admin)

  private def mkCfgPath() =
    System
      .property("user.dir")
      .map(_.getOrElse(".") + "/" + "application.conf")
      .map(Paths.get(_))

  val defaultConfig: ZLayer[System, Serializable, Configuration] =
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

  val testConfiguration: ULayer[TestConfiguration] = ZLayer.succeed(new TestConfiguration)
}

class TestConfiguration extends Configuration.Service {

  val config: Config = Config(
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
