package votbot

import votbot.model.irc.{ Channel, ChannelKey, User, UserKey }
import zio.ZIO
import zio.test.Assertion._
import zio.test._

object ApiSpec {

  val tests = suite("Api spec")(
    testM("addChannel creates channel") {
      for {
        api        <- ZIO.access[Api](_.api)
        chName     = "#votbot"
        srcChannel = Channel(chName, List.empty, Set.empty)
        _          <- api.addChannel(srcChannel)
        channel    <- api.getChannel(ChannelKey(chName))
      } yield assert(channel, equalTo(srcChannel))
    },
    testM("addUser creates user") {
      for {
        api        <- ZIO.access[Api](_.api)
        chName     = "#votbot"
        uName      = "votBot"
        srcChannel = Channel(chName, List.empty, Set.empty)
        _          <- api.addChannel(srcChannel)
        ch         <- api.getChannel(ChannelKey(chName))
        srcUser    = User(uName, Set(ChannelKey(ch.name)))
        _          <- api.addUser(srcUser)
        user       <- api.getUser(UserKey(uName))
      } yield assert(user, equalTo(srcUser))
    },
    testM("addChannelToUser add new channel to user's channel set") {
      for {
        api    <- ZIO.access[Api](_.api)
        user   <- api.getOrCreateUser("votbot")
        chName = "#votbot"
        _      <- api.addChannel(Channel(chName, List.empty, Set.empty))
        _      <- api.addChannelToUser(ChannelKey(chName), UserKey(user.name))
        nUser  <- api.getUser(UserKey(user.name))
      } yield assert(nUser.channels, equalTo(Set[ChannelKey](ChannelKey(chName))))
    },
    testM("removeChannel removes channel") {
      for {
        api        <- ZIO.access[Api](_.api)
        chName     = "#votbot"
        srcChannel = Channel(chName, List.empty, Set.empty)
        _          <- api.addChannel(srcChannel)
        _          <- api.removeChannel(ChannelKey(chName))
        res        <- api.allChannels()
      } yield assert(res, isEmpty)
    },
    testM("removeUser removes user from knownUsers and all channels") {
      for {
        api        <- ZIO.access[Api](_.api)
        chName     = "#votbot"
        srcChannel = Channel(chName, List.empty, Set.empty)
        _          <- api.addChannel(srcChannel)
        uName      = "votbot"
        srcUser    = User(uName, Set(ChannelKey(chName)))
        _          <- api.addUser(srcUser)
        _          <- api.removeUser(srcUser)
        mbUser     <- api.findUser(srcUser.name)
        channels   <- api.allChannels()
      } yield assert(mbUser, isNone) && assert(channels.filter(_.members.contains(UserKey(uName))), isEmpty)
    }
  )
}
