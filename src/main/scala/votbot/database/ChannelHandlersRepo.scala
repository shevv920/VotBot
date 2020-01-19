package votbot.database

import slick.basic.BasicBackend
import votbot.database.ChannelHandlersTable.ChannelHandlers
import votbot.interop.SlickToZio
import votbot.model.DB.ChannelHandler
import votbot.model.DBError
import votbot.model.irc.ChannelKey
import zio.{ Task, ZIO }

trait ChannelHandlersRepo {
  def createSchemaIfNotExists: ZIO[Any, DBError, Unit]
  def findByChannelKey(channelKey: ChannelKey): ZIO[Any, DBError, List[ChannelHandler]]
  def findEnabled(channelKey: ChannelKey): ZIO[Any, DBError, List[ChannelHandler]]
  def enable(channelKey: ChannelKey, handler: String): ZIO[Any, DBError, Unit]
  def disable(channelKey: ChannelKey, handler: String): ZIO[Any, DBError, Unit]
}

class SQLiteChannelHandlersRepo(implicit val dbTask: Task[BasicBackend#DatabaseDef]) extends ChannelHandlersRepo {
  import slick.jdbc.SQLiteProfile.api._
  private val handlers = TableQuery[ChannelHandlers]

  override def createSchemaIfNotExists: ZIO[Any, DBError, Unit] =
    SlickToZio(handlers.schema.createIfNotExists)
      .refineOrDie(e => DBError("ChannelHandlers schema creation error: " + e.getMessage, e))

  def findEnabled(channelKey: ChannelKey): ZIO[Any, DBError, List[ChannelHandler]] = {
    val q = handlers.filter(h => h.channelKey === channelKey && h.enabled === true)
    SlickToZio(q.result)
      .map(_.toList)
      .refineOrDie(e => DBError("ChannelHandlers find error: " + e.getMessage, e))
  }

  override def findByChannelKey(channelKey: ChannelKey): ZIO[Any, DBError, List[ChannelHandler]] = {
    val q = handlers.filter(_.channelKey === channelKey)
    SlickToZio(q.result)
      .map(_.toList)
      .refineOrDie(e => DBError("ChannelHandlers find by channel error: " + e.getMessage, e))
  }

  override def enable(channelKey: ChannelKey, handler: String): ZIO[Any, DBError, Unit] = {
    val q = handlers
      .filter(h => h.channelKey === channelKey && h.handler === handler)
      .map(_.enabled)
      .update(true)
    SlickToZio(q)
      .refineOrDie(e => DBError("ChannelHandlers update error: " + e.getMessage, e))
      .unit
  }

  override def disable(channelKey: ChannelKey, handler: String): ZIO[Any, DBError, Unit] = {
    val q = handlers
      .filter(h => h.channelKey === channelKey && h.handler === handler)
      .map(_.enabled)
      .update(false)
    SlickToZio(q)
      .refineOrDie(e => DBError("ChannelHandlers update error: " + e.getMessage, e))
      .unit
  }
}
