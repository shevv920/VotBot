package votbot

import votbot.database.{ Database, DefaultDatabase }
import votbot.model.DB.ChannelSetting
import votbot.model.irc.ChannelKey
import zio.ZIO
import zio.App
import zio.system._

object InitDB extends App {

  def mkEnvironment: ZIO[Any, Throwable, Database with System] =
    ZIO.effect(
      new Database with System.Live {
        override val database: Database.Service[Any] = new DefaultDatabase
      }
    )

  def main: ZIO[Database with System, Serializable, Unit] =
    for {
      cfg <- Main.readConfig()
      db  <- ZIO.access[Database](_.database)
      _   <- db.quotesRepo.createSchemaIfNotExists
      _   <- db.channelSettingsRepo.createSchemaIfNotExists
      _   <- db.channelHandlersRepo.createSchemaIfNotExists

      autoJoinChannelsSettings = cfg.bot.autoJoinChannels.map(c => ChannelSetting(ChannelKey(c), autoJoin = true))
      _                        <- db.channelSettingsRepo.insertAll(autoJoinChannelsSettings)
    } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    main.provideSomeM(mkEnvironment).either.map(_.fold(e => { println(e); 1 }, _ => 0))
}
