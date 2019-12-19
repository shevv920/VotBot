package votbot.database

import votbot.database.ChannelHandlersTable.ChannelHandlers
import votbot.interop.ZioSlick
import votbot.model.DB.ChannelHandler
import votbot.model.DBError
import votbot.model.irc.ChannelKey
import zio.ZIO

trait ChannelHandlersRepo {
  val channelHandlersRepo: ChannelHandlersRepo.Service[Any]
}

object ChannelHandlersRepo {

  trait Service[R] {
    def createSchemaIfNotExists: ZIO[R, DBError, Unit]
    def findByChannelKey(channelKey: ChannelKey): ZIO[R, DBError, List[ChannelHandler]]
    def findEnabled(channelKey: ChannelKey): ZIO[R, DBError, List[ChannelHandler]]
    def enable(channelKey: ChannelKey, handler: String): ZIO[R, DBError, Unit]
    def disable(channelKey: ChannelKey, handler: String): ZIO[R, DBError, Unit]
  }
}

trait SqliteChannelHandlersRepo extends ChannelHandlersRepo { self: DatabaseProvider =>
  import slick.jdbc.SQLiteProfile.api._
  private val handlers = TableQuery[ChannelHandlers]

  override val channelHandlersRepo: ChannelHandlersRepo.Service[Any] =
    new ChannelHandlersRepo.Service[Any] {

      override def createSchemaIfNotExists: ZIO[Any, DBError, Unit] =
        ZioSlick(handlers.schema.createIfNotExists)
          .refineOrDie(e => DBError("ChannelHandlers schema creation error: " + e.getMessage, e))
          .provide(self)

      def findEnabled(channelKey: ChannelKey): ZIO[Any, DBError, List[ChannelHandler]] = {
        val q = handlers.filter(h => h.channelKey === channelKey && h.enabled === true)
        ZioSlick(q.result)
          .map(_.toList)
          .refineOrDie(e => DBError("ChannelHandlers find error: " + e.getMessage, e))
          .provide(self)
      }

      override def findByChannelKey(channelKey: ChannelKey): ZIO[Any, DBError, List[ChannelHandler]] = {
        val q = handlers.filter(_.channelKey === channelKey)
        ZioSlick(q.result)
          .map(_.toList)
          .refineOrDie(e => DBError("ChannelHandlers find by channel error: " + e.getMessage, e))
          .provide(self)
      }

      override def enable(channelKey: ChannelKey, handler: String): ZIO[Any, DBError, Unit] = {
        val q = handlers
          .filter(h => h.channelKey === channelKey && h.handler === handler)
          .map(_.enabled)
          .update(true)
        ZioSlick(q)
          .refineOrDie(e => DBError("ChannelHandlers update error: " + e.getMessage, e))
          .unit
          .provide(self)
      }

      override def disable(channelKey: ChannelKey, handler: String): ZIO[Any, DBError, Unit] = {
        val q = handlers
          .filter(h => h.channelKey === channelKey && h.handler === handler)
          .map(_.enabled)
          .update(false)
        ZioSlick(q)
          .refineOrDie(e => DBError("ChannelHandlers update error: " + e.getMessage, e))
          .unit
          .provide(self)
      }
    }
}
