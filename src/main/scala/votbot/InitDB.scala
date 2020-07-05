package votbot

import votbot.Configuration.Configuration
import votbot.database.Database.Database
import votbot.database.{ Database, DefaultDatabase }
import votbot.model.DB.ChannelPrefs
import votbot.model.irc.ChannelKey
import zio.{ App, ExitCode, ZIO }
import zio.system._

object InitDB extends App {

  val env = System.live ++ Database.defaultDatabase ++ Configuration.defaultConfig

  def main: ZIO[Configuration with Database with System, Serializable, Unit] =
    for {
      botCfg <- Configuration.bot
      db     <- ZIO.access[Database](_.get)
      _      <- db.quotesRepo.createSchemaIfNotExists
      _      <- db.channelSettingsRepo.createSchemaIfNotExists
      _      <- db.channelHandlersRepo.createSchemaIfNotExists

      autoJoinChannelsPrefs = botCfg.autoJoinChannels.map(c => ChannelPrefs(ChannelKey(c), autoJoin = true))
      _                     <- db.channelSettingsRepo.insertAll(autoJoinChannelsPrefs)
    } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    main.provideSomeLayer(env).either.map(_.fold(e => { println(e); ExitCode.failure }, _ => ExitCode.success))
}
