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
    def createSchemaIfNotExists: ZIO[R, DBError, Unit]
    def findByKey(chKey: ChannelKey): ZIO[R, DBError, Option[ChannelSetting]]
    def update(channelKey: ChannelKey, cs: ChannelSetting): ZIO[R, DBError, Int]
  }
}

trait SqliteChannelSettingsRepo extends ChannelSettingsRepo { self: DatabaseProvider =>
  private val settings = TableQuery[ChannelSettings]
  import slick.jdbc.SQLiteProfile.api._

  override val channelSettingsRepo: ChannelSettingsRepo.Service[Any] =
    new ChannelSettingsRepo.Service[Any] {

      override def createSchemaIfNotExists: ZIO[Any, DBError, Unit] =
        ZioSlick(settings.schema.createIfNotExists)
          .refineOrDie(e => DBError("ChannelSettingsRepo Schema creation error: " + e.getMessage, e))
          .provide(self)

      override def findByKey(chKey: ChannelKey): ZIO[Any, DBError, Option[ChannelSetting]] = {
        val q = settings.filter(_.channelKey === chKey)
        ZioSlick(q.result)
          .map(_.headOption)
          .refineOrDie(e => DBError("Channel Settings Repo error: " + e.getMessage, e))
          .provide(self)
      }

      override def update(chKey: ChannelKey, cs: ChannelSetting): ZIO[Any, DBError, Int] = {
        val q = settings.filter(_.channelKey === chKey).update(cs)
        ZioSlick(q).refineOrDie(e => DBError("Channel Settings Repo error: " + e.getMessage, e)).provide(self)
      }
    }
}
