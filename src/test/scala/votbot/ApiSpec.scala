package votbot
import votbot.model.Irc.{Channel, User}
import zio.ZIO
import zio.test.Assertion._
import zio.test._

object ApiSpec {

  val tests = suite("Api spec")(
    testM("addChannel creates channel") {
      for {
        api        <- ZIO.environment[Api]
        chName     = "#votbot"
        srcChannel = Channel(chName, List.empty, Set.empty)
        _          <- api.addChannel(srcChannel)
        channel    <- api.getChannel(chName)
      } yield assert(channel, equalTo(srcChannel))
    },
    testM("addUser creates user") {
      for {
        api        <- ZIO.environment[Api]
        chName     = "#votbot"
        uName      = "votbot"
        srcChannel = Channel(chName, List.empty, Set.empty)
        _          <- api.addChannel(srcChannel)
        ch         <- api.getChannel(chName)
        srcUser    = User(uName, Set(ch.name))
        _          <- api.addUser(srcUser)
        user       <- api.getUser(uName)
      } yield assert(user, equalTo(srcUser))
    },
    testM("addChannelToUser add new channel to user's channel set") {
      for {
        api    <- ZIO.environment[Api]
        user   <- api.getOrCreateUser("votbot")
        chName = "#votbot"
        _      <- api.addChannel(Channel(chName, List.empty, Set.empty))
        _      <- api.addChannelToUser(chName, user.name)
        nUser  <- api.getUser(user.name)
      } yield assert(nUser.channels, equalTo(Set(chName)))
    },
    testM("removeChannel removes channel") {
      for {
        api        <- ZIO.environment[Api]
        chName     = "#votbot"
        srcChannel = Channel(chName, List.empty, Set.empty)
        _          <- api.addChannel(srcChannel)
        _          <- api.removeChannel(chName)
        res        <- api.allChannels()
      } yield assert(res, isEmpty)
    },
    testM("removeUser removes user from knownUsers and all channels") {
      for {
        api        <- ZIO.environment[Api]
        chName     = "#votbot"
        srcChannel = Channel(chName, List.empty, Set.empty)
        _          <- api.addChannel(srcChannel)
        uName      = "votbot"
        srcUser    = User(uName, Set(chName.toLowerCase))
        _          <- api.addUser(srcUser)
        _          <- api.removeUser(srcUser)
        mbUser     <- api.findUser(srcUser.name)
        channels   <- api.allChannels()
      } yield assert(mbUser, isNone) && assert(channels.filter(_.members.contains(uName)), isEmpty)
    }
  )
}
