package votbot.event

import votbot.Api
import votbot.Main.VotbotEnv
import votbot.command.Command
import votbot.event.CustomHandlers.Handle
import votbot.event.Event.{ CommandTriggered, IncomingMessage, Join }
import zio.{ Ref, ZIO }

object DefaultCustomHandlers {
  val commandPrefix = "!"

  val helloOnJoin: Handle = {
    case Join(user, channel) =>
      Api.>.sendChannelMessage(channel, s"Hello ${user.value}")
  }

  val commandTrigger: Handle = {
    case IncomingMessage(sender, _, msg) if msg.startsWith(commandPrefix) =>
      for {
        api <- ZIO.access[Api](_.api)
        cmd <- Command.fromString(msg.drop(1))
        _   <- api.enqueueEvent(CommandTriggered(cmd, sender))
      } yield ()
  }

}

trait DefaultCustomHandlers extends CustomHandlers.Service[Any] {
  val handlers: Ref[Set[Handle]]
  val handleFunction: Ref[Handle]

  private def mkHandleFunction =
    for {
      hs <- handlers.get
      hf = hs.foldLeft[Handle](PartialFunction.empty)(_.orElse(_))
    } yield hf

  private def updateHandleFunction() =
    for {
      hf <- mkHandleFunction
      _  <- handleFunction.update(_ => hf)
    } yield ()

  override def register(handler: Handle): ZIO[Any, Throwable, Unit] =
    handlers.update(hs => hs + handler) *> updateHandleFunction()

  override def unregister(handler: Handle): ZIO[Any, Throwable, Unit] =
    handlers.update(hs => hs - handler) *> updateHandleFunction()

  override def handle(event: Event): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      hf <- handleFunction.get
      _  <- ZIO.whenCase(event)(hf)
    } yield ()

}
