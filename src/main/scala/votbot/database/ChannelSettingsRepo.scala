package votbot.database

import slick.lifted.TableQuery
import votbot.database.ChannelSettingsTable.ChannelSettings
import votbot.interop.ZioSlick
import votbot.model.DB.ChannelSetting
import votbot.model.DBError
import votbot.model.irc.ChannelKey
import zio.ZIO

trait ChannelSettingsRepo {
  val channelSettingsRepo: ChannelSettingsRepo.Service[Any]
}

object ChannelSettingsRepo {

  trait Service[R] {
    def createSchemaIfNotExists: ZIO[DatabaseProvider, DBError, Unit]
    def findByKey(chKey: ChannelKey): ZIO[DatabaseProvider, DBError, Option[ChannelSetting]]
    def update(channelKey: ChannelKey, cs: ChannelSetting): ZIO[DatabaseProvider, DBError, Int]
  }
}

object TestChannelSettingsRepo extends ChannelSettingsRepo.Service[Any] {
  val settings = TableQuery[ChannelSettings]
  import slick.jdbc.SQLiteProfile.api._

  override def createSchemaIfNotExists: ZIO[DatabaseProvider, DBError, Unit] =
    ZioSlick(settings.schema.createIfNotExists)
      .refineOrDie(e => DBError("ChannelSettingsRepo Schema creation error: " + e.getMessage, e))

  override def findByKey(chKey: ChannelKey): ZIO[DatabaseProvider, DBError, Option[ChannelSetting]] = {
    val q = settings.filter(_.channelKey === chKey)
    ZioSlick(q.result).map(_.headOption).refineOrDie(e => DBError("Channel Settings Repo error: " + e.getMessage, e))
  }

  override def update(chKey: ChannelKey, cs: ChannelSetting): ZIO[DatabaseProvider, DBError, Int] = {
    val q = settings.filter(_.channelKey === chKey).update(cs)
    ZioSlick(q).refineOrDie(e => DBError("Channel Settings Repo error: " + e.getMessage, e))
  }
}
