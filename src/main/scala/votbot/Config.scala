package votbot

import java.nio.file.Paths

import pureconfig.ConfigSource
import zio.ZLayer.NoDeps
import zio.{ Has, URIO, ZIO, ZLayer, system }
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

  def bot: URIO[Configuration, BotProps] = ZIO.access[Configuration](_.get.bot)

  private def mkCfgPath() =
    system
      .property("user.dir")
      .map(_.getOrElse(".") + "/" + "application.conf")
      .map(Paths.get(_))

  val defaultConfig: ZLayer[system.System, Serializable, Configuration] =
    ZLayer.fromEffect(for {
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
    })

  val testConfiguration: NoDeps[Nothing, Has[TestConfiguration]] = ZLayer.succeed(new TestConfiguration)
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
