package votbot.event

import votbot.BotState.BotState
import votbot.event.Event.{CapabilityAck, ExtendedJoin, UserLoggedIn, UserLoggedOut}
import votbot.model.irc._
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.{assert, suite, test}

object EventSpec {

  val tests = suite("Event ")(
    test("Recognize CAP * ACK msg") {
      for {
        event <- Event.fromIrcMessage(Message(Command.Cap, List("*", "ACK", "cap1 cap2 cap3")))
      } yield assert(event)(equalTo(CapabilityAck(List("cap1 cap2 cap3".split("\\s"): _*))))
    },
    test("Recognize CAP * ACK : msg (empty caps list)") {
      for {
        event <- Event.fromIrcMessage(Message(Command.Cap, List("*", "ACK", "")))
      } yield assert(event)(equalTo(CapabilityAck(List(""))))
    },
    test("Recognize ACCOUNT accountName (logged in msg)") {
      val prefix = Prefix("nick", "user", "host")
      for {
        event <- Event.fromIrcMessage(
                  Message(Command.Account, List("accountName"), Some(prefix))
                )
      } yield assert(event)(equalTo(UserLoggedIn(UserKey(prefix.nick), "accountName")))
    },
    test("Recognize ACCOUNT * msg (logged out)") {
      val prefix = Prefix("nick", "user", "host")
      for {
        event <- Event.fromIrcMessage(
                  Message(Command.Account, List("*"), Some(prefix))
                )
      } yield assert(event)(equalTo(UserLoggedOut(UserKey(prefix.nick))))
    },
    test("Recognize Join msg when its extended join capability") {
      val prefix = Prefix("nick", "user", "host")
      val msg    = Message(Command.Join, List("#votbot", "*", ""), Some(prefix))
      for {
        _     <- ZIO.environmentWithZIO[BotState](_.get.addCapabilities(Capabilities.ExtendedJoin))
        event <- Event.fromIrcMessage(msg)
      } yield assert(event)(equalTo(ExtendedJoin(UserKey("nick"), ChannelKey("#votbot"), "*")))
    }
  )
}
