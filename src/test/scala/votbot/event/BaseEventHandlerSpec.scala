package votbot.event

import votbot.Api
import votbot.Api.Api
import votbot.event.Event._
import votbot.event.EventHandler.EventHandler
import votbot.model.irc._
import zio.ZIO
import zio.nio.{ InetSocketAddress }
import zio.test.Assertion._
import zio.test._

object BaseEventHandlerSpec {

  val tests = suite("BaseEventHandler")(
    test("response for ping request with/with no args ") {
      for {
        handler     <- ZIO.service[EventHandler]
        evt         = Ping(None)
        args        = "irc.foo.net"
        evtWithArgs = Ping(Some(args))
        _           <- handler.handle(evt)
        _           <- handler.handle(evtWithArgs)
        pongNoArgs  <- Api.dequeueOutMessage()
        pongArgs    <- Api.dequeueOutMessage()
      } yield assert(pongNoArgs)(equalTo(Message(Command.Pong))) &&
        assert(pongArgs)(equalTo(Message(Command.Pong, List(args))))
    },
    test("send nick and user commands on Connected Event") {
      for {
        handler <- ZIO.service[EventHandler]
        addr    <- InetSocketAddress.localHost(1234)
        _       <- handler.handle(Connected(addr))
        all     <- Api.dequeueAllOutMessages()
      } yield assert(all.count(c => c.cmd == Command.Nick || c.cmd == Command.User))(equalTo(2))
    },
    test("BotJoin creates channel") {
      for {
        api     <- ZIO.service[Api]
        handler <- ZIO.service[EventHandler]
        chName  = "#votbot"
        _       <- handler.handle(BotJoin(chName))
        ch      <- api.getChannel(ChannelKey(chName))
      } yield assert(ch)(equalTo(Channel(chName, List(), Set(), Set(), PartialFunction.empty)))
    },
    test("event Join adds user to channel and channel to user's channels") {
      for {
        api     <- ZIO.service[Api]
        handler <- ZIO.service[EventHandler]
        uName   = "testName"
        chName  = "#votbot"
        _       <- handler.handle(BotJoin(chName))
        _       <- handler.handle(Join(UserKey(uName), ChannelKey(chName)))
        ch      <- api.getChannel(ChannelKey(chName))
        user    <- api.getUser(UserKey(uName))
      } yield assert(ch.members)(equalTo(Set(UserKey(user.name)))) &&
        assert(user.channels)(equalTo(Set(ChannelKey(ch.name))))
    },
    test("Quit removes user from everywhere") {
      for {
        api          <- ZIO.service[Api]
        handler      <- ZIO.service[EventHandler]
        uName        = "testName"
        chName       = "#votbot"
        _            <- handler.handle(BotJoin(chName))
        _            <- handler.handle(Join(UserKey(uName), ChannelKey(chName)))
        ch           <- api.getChannel(ChannelKey(chName))
        user         <- api.findUser(uName)
        _            <- handler.handle(Quit(UserKey(uName), "Leaving."))
        cleanChannel <- api.getChannel(ChannelKey(chName))
        mbUser       <- api.findUser(uName)
      } yield assert(ch.members)(equalTo(Set(UserKey(uName)))) &&
        assert(user.nonEmpty)(isTrue) &&
        assert(cleanChannel.members)(isEmpty) &&
        assert(mbUser)(isNone)
    },
    test("Part removes user from channel and channel from user") {
      for {
        api     <- ZIO.service[Api]
        handler <- ZIO.service[EventHandler]
        uName   = "testName"
        chName  = "#votbot"
        _       <- handler.handle(BotJoin(chName))
        _       <- handler.handle(Join(UserKey(uName), ChannelKey(chName)))
        _       <- handler.handle(Part(UserKey(uName), ChannelKey(chName), "reason"))
        ch      <- api.getChannel(ChannelKey(chName))
        u       <- api.getUser(UserKey(uName))
      } yield assert(ch.members)(isEmpty) && assert(u.channels)(isEmpty)
    },
    test("NamesList add list of users to channel members") {
      for {
        api     <- ZIO.service[Api]
        handler <- ZIO.service[EventHandler]
        chName  = "#votbot"
        _       <- handler.handle(BotJoin(chName))
        names = List(
          ("nicK1", List.empty[ChannelMode]),
          ("Nick2", List.empty[ChannelMode]),
          ("niCk3", List.empty[ChannelMode])
        )
        _       <- handler.handle(NamesList(ChannelKey(chName), names))
        channel <- api.getChannel(ChannelKey(chName))
        nick1   <- api.getUser(UserKey("nick1"))
      } yield assert(channel.members.size)(equalTo(3)) && assert(nick1.name)(equalTo("nicK1"))
    }
  )
}
