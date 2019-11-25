package votbot.event
import votbot.Api
import votbot.event.Event.{BotJoin, Connected, Join, Ping}
import votbot.event.handlers.BaseEventHandler
import votbot.model.Irc
import votbot.model.Irc.{Channel, Command, RawMessage}
import zio.ZIO
import zio.test.Assertion._
import zio.test._

object BaseEventHandlerSpec {

  val tests = suite("BaseEventHandler")(
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
    },
    testM("send nick and user commands on Connected Event") {
      for {
        api     <- ZIO.environment[Api]
        handler <- ZIO.environment[BaseEventHandler]
        _       <- handler.handle(Connected)
        nickCmd <- api.dequeueOutMessage()
        userCmd <- api.dequeueOutMessage()
      } yield assert(nickCmd.cmd, equalTo(Irc.Command.Nick)) && assert(userCmd.cmd, equalTo(Irc.Command.User))
    },
    testM("BotJoin creates channel") {
      for {
        api     <- ZIO.environment[Api]
        handler <- ZIO.environment[BaseEventHandler]
        chName  = "#votbot"
        _       <- handler.handle(BotJoin(chName))
        ch      <- api.getChannel(chName)
      } yield assert(ch, equalTo(Channel(chName, List(), Set())))
    },
    testM("BotJoin creates channel, Join adds user to channel and channel to user") {
      for {
        api     <- ZIO.environment[Api]
        handler <- ZIO.environment[BaseEventHandler]
        uName   = "testName"
        chName  = "#votbot"
        _       <- handler.handle(BotJoin(chName))
        _       <- handler.handle(Join(uName, chName))
        ch      <- api.getChannel(chName)
        user    <- api.getUser(uName)
      } yield assert(ch, equalTo(Channel(chName, List.empty, Set(user.name)))) &&
        assert(user.channels, contains(ch.name))
    }
  )
}
