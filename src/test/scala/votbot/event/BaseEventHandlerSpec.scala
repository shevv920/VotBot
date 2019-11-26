package votbot.event
import votbot.Api
import votbot.event.Event._
import votbot.event.handlers.BaseEventHandler
import votbot.model.Irc
import votbot.model.Irc.{Channel, ChannelKey, ChannelMode, Command, RawMessage, UserKey}
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
    testM("event Join adds user to channel and channel to user's channels") {
      for {
        api     <- ZIO.environment[Api]
        handler <- ZIO.environment[BaseEventHandler]
        uName   = "testName"
        chName  = "#votbot"
        _       <- handler.handle(BotJoin(chName))
        _       <- handler.handle(Join(uName, chName))
        ch      <- api.getChannel(chName)
        user    <- api.getUser(uName)
      } yield assert(ch.members, equalTo(Set[UserKey](user.name))) &&
        assert(user.channels, equalTo(Set[ChannelKey](ch.name)))
    },
    testM("Quit removes user from everywhere") {
      for {
        api          <- ZIO.environment[Api]
        handler      <- ZIO.environment[BaseEventHandler]
        uName        = "testName"
        chName       = "#votbot"
        _            <- handler.handle(BotJoin(chName))
        _            <- handler.handle(Join(uName, chName))
        ch           <- api.getChannel(chName)
        user         <- api.findUser(uName)
        _            <- handler.handle(Quit(uName, "Leaving."))
        cleanChannel <- api.getChannel(chName)
        mbUser       <- api.findUser(uName)
      } yield assert(ch.members, equalTo(Set[UserKey](uName))) &&
        assert(user.nonEmpty, isTrue) &&
        assert(cleanChannel.members, isEmpty) &&
        assert(mbUser, isNone)
    },
    testM("Part removes user from channel and channel from user") {
      for {
        api     <- ZIO.environment[Api]
        handler <- ZIO.environment[BaseEventHandler]
        uName   = "testName"
        chName  = "#votbot"
        _       <- handler.handle(BotJoin(chName))
        _       <- handler.handle(Join(uName, chName))
        _       <- handler.handle(Part(uName, chName, "reason"))
        ch      <- api.getChannel(chName)
        u       <- api.getUser(uName)
      } yield assert(ch.members, isEmpty) && assert(u.channels, isEmpty)
    },
    testM("NamesList add list of users to channel members") {
      for {
        api     <- ZIO.environment[Api]
        handler <- ZIO.environment[BaseEventHandler]
        chName  = "#votbot"
        _       <- handler.handle(BotJoin(chName))
        names = List(
          ("nick1", List.empty[ChannelMode]),
          ("nick2", List.empty[ChannelMode]),
          ("nick3", List.empty[ChannelMode])
        )
        _ <- handler.handle(NamesList(chName, names))
        channel <- api.getChannel(chName)
      } yield assert(channel.members.size, equalTo(3))
    }
  )
}
