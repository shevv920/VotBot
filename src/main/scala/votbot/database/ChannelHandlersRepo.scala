package votbot.database

import votbot.database.ChannelHandlersTable.ChannelHandlers
import votbot.interop.ZioSlick
import votbot.model.DB.ChannelHandler
import votbot.model.DBError
import votbot.model.irc.ChannelKey
import zio.ZIO

trait ChannelHandlersRepo {
  val channelHandlersRepo: ChannelHandlersRepo.Service[DatabaseProvider]
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

trait TestChannelHandlersRepo extends ChannelHandlersRepo {
  import slick.jdbc.SQLiteProfile.api._
  private val handlers = TableQuery[ChannelHandlers]

  override val channelHandlersRepo: ChannelHandlersRepo.Service[DatabaseProvider] =
    new ChannelHandlersRepo.Service[DatabaseProvider] {

      override def createSchemaIfNotExists: ZIO[DatabaseProvider, DBError, Unit] =
        ZioSlick(handlers.schema.createIfNotExists)
          .refineOrDie(e => DBError("ChannelHandlers schema creation error: " + e.getMessage, e))

      def findEnabled(channelKey: ChannelKey): ZIO[DatabaseProvider, DBError, List[ChannelHandler]] = {
        val q = handlers.filter(h => h.channelKey === channelKey && h.enabled === true)
        ZioSlick(q.result)
          .map(_.toList)
          .refineOrDie(e => DBError("ChannelHandlers find error: " + e.getMessage, e))
      }

      override def findByChannelKey(channelKey: ChannelKey): ZIO[DatabaseProvider, DBError, List[ChannelHandler]] = {
        val q = handlers.filter(_.channelKey === channelKey)
        ZioSlick(q.result)
          .map(_.toList)
          .refineOrDie(e => DBError("ChannelHandlers find error: " + e.getMessage, e))
      }

      override def enable(channelKey: ChannelKey, handler: String): ZIO[DatabaseProvider, DBError, Unit] = {
        val q = handlers
          .filter(h => h.channelKey === channelKey && h.handler === handler)
          .map(_.enabled)
          .update(true)
        ZioSlick(q)
          .refineOrDie(e => DBError("ChannelHandlers update error: " + e.getMessage, e))
          .unit
      }

      override def disable(channelKey: ChannelKey, handler: String): ZIO[DatabaseProvider, DBError, Unit] = {
        val q = handlers
          .filter(h => h.channelKey === channelKey && h.handler === handler)
          .map(_.enabled)
          .update(false)
        ZioSlick(q)
          .refineOrDie(e => DBError("ChannelHandlers update error: " + e.getMessage, e))
          .unit
      }
    }
}
