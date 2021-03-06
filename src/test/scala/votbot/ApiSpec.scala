package votbot

import votbot.Api.Api
import votbot.model.irc.{ Channel, ChannelKey, User, UserKey }
import zio.ZIO
import zio.test.Assertion._
import zio.test._

object ApiSpec {
  private val testUserName1    = "testUserName1"
  private val testUserName2    = "testUserName2"
  private val testEmptyUser    = User(testUserName1, Set.empty, None)
  private val testChannelName  = "#test_channel"
  private val testEmptyChannel = Channel(testChannelName, List.empty, Set.empty, Set.empty, PartialFunction.empty)

  val tests = suite("Api spec")(
    testM("addChannel creates channel") {
      for {
        api     <- ZIO.access[Api](_.get)
        _       <- api.addChannel(testEmptyChannel)
        channel <- api.getChannel(ChannelKey(testEmptyChannel.name))
      } yield assert(channel)(equalTo(testEmptyChannel))
    },
    testM("addUser creates user") {
      for {
        api     <- ZIO.access[Api](_.get)
        srcUser = testEmptyUser
        _       <- api.addUser(srcUser)
        user    <- api.getUser(UserKey(testEmptyUser.name))
      } yield assert(user)(equalTo(srcUser))
    },
    testM("addChannelToUser add new channel to user's channel set") {
      for {
        api   <- ZIO.access[Api](_.get)
        user  <- api.getOrCreateUser(testUserName1)
        _     <- api.addChannel(testEmptyChannel)
        _     <- api.addChannelToUser(ChannelKey(testEmptyChannel.name), UserKey(user.name))
        nUser <- api.getUser(UserKey(user.name))
      } yield assert(nUser.channels)(equalTo(Set[ChannelKey](ChannelKey(testEmptyChannel.name))))
    },
    testM("removeChannel removes channel") {
      for {
        api <- ZIO.access[Api](_.get)
        _   <- api.addChannel(testEmptyChannel)
        _   <- api.removeChannel(ChannelKey(testEmptyChannel.name))
        res <- api.allChannels()
      } yield assert(res)(isEmpty)
    },
    testM("removeUser removes user from knownUsers and all channels") {
      for {
        api      <- ZIO.access[Api](_.get)
        _        <- api.addChannel(testEmptyChannel)
        srcUser  = User(testUserName1, Set(ChannelKey(testEmptyChannel.name)))
        _        <- api.addUser(srcUser)
        _        <- api.removeUser(srcUser)
        mbUser   <- api.findUser(srcUser.name)
        channels <- api.allChannels()
      } yield assert(mbUser)(isNone) && assert(channels.filter(_.members.contains(UserKey(testUserName1))))(isEmpty)
    },
    testM("change nick removes old user and adds new one") {
      for {
        api  <- ZIO.access[Api](_.get)
        _    <- api.addUser(testEmptyUser)
        _    <- api.changeUserNick(testUserName1, testUserName2)
        oldU <- api.findUser(testUserName1)
        newU <- api.findUser(testUserName2)
      } yield assert(oldU)(isNone) && assert(newU)(isSome(equalTo(testEmptyUser.copy(name = testUserName2))))
    },
    testM("change nick removes accountName from new user") {
      for {
        api  <- ZIO.access[Api](_.get)
        _    <- api.addUser(testEmptyUser)
        _    <- api.changeUserNick(testUserName1, testUserName2)
        newU <- api.getUser(UserKey(testUserName2))
      } yield assert(newU.accountName)(isNone)
    },
    testM("change nick affects user channels") {
      for {
        api   <- ZIO.access[Api](_.get)
        _     <- api.addUser(testEmptyUser)
        _     <- api.addChannel(testEmptyChannel)
        _     <- api.addChannelToUser(ChannelKey(testEmptyChannel.name), UserKey(testEmptyUser.name))
        _     <- api.addUserToChannel(ChannelKey(testEmptyChannel.name), testEmptyUser)
        _     <- api.changeUserNick(testUserName1, testUserName2)
        oldU  <- api.findUser(testUserName1)
        newU  <- api.findUser(testUserName2)
        newCh <- api.getChannel(ChannelKey(testEmptyChannel.name))
      } yield assert(oldU)(isNone) &&
        assert(newCh.members)(contains(UserKey(testUserName2))) &&
        assert(newU.nonEmpty)(isTrue) &&
        assert(newU.get.channels.contains(ChannelKey(testEmptyChannel.name)))(isTrue)
    }
  )
}
