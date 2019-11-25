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
    }
  )
}
