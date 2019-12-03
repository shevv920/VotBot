package votbot.event
import votbot.event.Event.{ CapabilityAck, UserLoggedIn, UserLoggedOut }
import votbot.model.irc.{ Command, Prefix, RawMessage }
import zio.test.Assertion.equalTo
import zio.test.{ assert, suite, testM }

object EventSpec {

  val tests = suite("Event.ircToEvent")(
    testM("Recognize CAP * ACK msg") {
      for {
        event <- Event.ircToEvent(RawMessage(Command.Cap, Vector("*", "ACK", "cap1 cap2 cap3")))
      } yield assert(event, equalTo(CapabilityAck(List("cap1 cap2 cap3".split("\\s"): _*))))
    },
    testM("Recognize CAP * ACK : msg (empty caps list)") {
      for {
        event <- Event.ircToEvent(RawMessage(Command.Cap, Vector("*", "ACK", "")))
      } yield assert(event, equalTo(CapabilityAck(List(""))))
    },
    testM("Recognize ACCOUNT accountName (logged in msg)") {
      val prefix = Prefix("nick", "user", "host")
      for {
        event <- Event.ircToEvent(
                  RawMessage(Command.Account, Vector("accountName"), Some(prefix))
                )
      } yield assert(event, equalTo(UserLoggedIn(prefix, "accountName")))
    },
    testM("Recognize ACCOUNT * msg (logged out)") {
      val prefix = Prefix("nick", "user", "host")
      for {
        event <- Event.ircToEvent(
                  RawMessage(Command.Account, Vector("*"), Some(prefix))
                )
      } yield assert(event, equalTo(UserLoggedOut(prefix)))
    }
  )
}
