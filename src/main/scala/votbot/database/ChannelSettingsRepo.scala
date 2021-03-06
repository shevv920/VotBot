package votbot.database

import slick.basic.BasicBackend
import slick.lifted.TableQuery
import votbot.database.ChannelSettingsTable.ChannelSettings
import votbot.interop.SlickToZio
import votbot.model.DB.ChannelPrefs
import votbot.model.DBError
import votbot.model.irc.ChannelKey
import zio.{ Task, ZIO }

trait ChannelSettingsRepo {
  def createSchemaIfNotExists: ZIO[Any, DBError, Unit]
  def getAll: ZIO[Any, DBError, List[ChannelPrefs]]
  def insertAll(cs: List[ChannelPrefs]): ZIO[Any, DBError, Option[Int]]
  def findByKey(chKey: ChannelKey): ZIO[Any, DBError, Option[ChannelPrefs]]
  def findAutoJoin(enabled: Boolean = true): ZIO[Any, DBError, List[ChannelKey]]
  def update(channelKey: ChannelKey, cs: ChannelPrefs): ZIO[Any, DBError, Int]
}

class SQLiteChannelSettingsRepo(implicit val dbTask: Task[BasicBackend#DatabaseDef]) extends ChannelSettingsRepo {

  private val settings = TableQuery[ChannelSettings]
  import slick.jdbc.SQLiteProfile.api._

  override def getAll: ZIO[Any, DBError, List[ChannelPrefs]] =
    SlickToZio(settings.result)
      .map(_.toList)
      .refineOrDie(e => DBError(s"ChannelSettingsRepo select all error ${e.getMessage}", e))

  override def createSchemaIfNotExists: ZIO[Any, DBError, Unit] =
    SlickToZio(settings.schema.createIfNotExists)
      .refineOrDie(e => DBError("ChannelSettingsRepo Schema creation error: " + e.getMessage, e))

  override def insertAll(cs: List[ChannelPrefs]): ZIO[Any, DBError, Option[Int]] = {
    val q = settings ++= cs
    SlickToZio(q)
      .refineOrDie(e => DBError(s"Error creating new Channel setting record: ${e.getMessage}", e))
  }

  override def findByKey(chKey: ChannelKey): ZIO[Any, DBError, Option[ChannelPrefs]] = {
    val q = settings.filter(_.channelKey === chKey)
    SlickToZio(q.result)
      .map(_.headOption)
      .refineOrDie(e => DBError("Channel Settings Repo error: " + e.getMessage, e))
  }

  override def findAutoJoin(enabled: Boolean = true): ZIO[Any, DBError, List[ChannelKey]] = {
    val q = settings.filter(_.autoJoin === true)
    SlickToZio(q.result)
      .map(_.toList.map(_.channelKey))
      .refineOrDie(e => DBError("Error when trying to find auto join channels" + e.getMessage, e))
  }

  override def update(chKey: ChannelKey, cs: ChannelPrefs): ZIO[Any, DBError, Int] = {
    val q = settings.filter(_.channelKey === chKey).update(cs)
    SlickToZio(q)
      .refineOrDie(e => DBError("Channel Settings Repo error: " + e.getMessage, e))
  }

}
