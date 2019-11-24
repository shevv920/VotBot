package votbot.event
import votbot.Api
import votbot.event.Event.Ping
import votbot.event.handlers.BaseEventHandler
import zio.ZIO
import zio.test._
import Assertion._
import votbot.model.Irc.{ Command, RawMessage }

object BaseEventHandlerSpec {

  val tests = suite("BaseEventHandler ") {
    testM("response for ping request with/with no args ") {
      for {
        api         <- ZIO.environment[Api]
        handler     <- ZIO.environment[BaseEventHandler]
        evt         = Ping(None)
        args        = "irc.foo.net"
        evtWithArgs = Ping(Some(args))
        _           <- handler.handle(evt)
        _           <- handler.handle(evtWithArgs)
        pongNoArgs  <- api.dequeueOutMessage()
        pongArgs    <- api.dequeueOutMessage()
      } yield assert(pongNoArgs, equalTo(RawMessage(Command.Pong))) &&
        assert(pongArgs, equalTo(RawMessage(Command.Pong, Vector(args))))
    }
  }
}
