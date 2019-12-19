package votbot.event
import votbot.BotState
import votbot.event.Event.{ CapabilityAck, ExtendedJoin, UserLoggedIn, UserLoggedOut }
import votbot.model.irc.{ Capabilities, Command, Message, Prefix }
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.{ assert, suite, testM }

object EventSpec {

  val tests = suite("Event.ircToEvent")(
    testM("Recognize CAP * ACK msg") {
      for {
        event <- Event.ircToEvent(Message(Command.Cap, Vector("*", "ACK", "cap1 cap2 cap3")))
      } yield assert(event, equalTo(CapabilityAck(List("cap1 cap2 cap3".split("\\s"): _*))))
    },
    testM("Recognize CAP * ACK : msg (empty caps list)") {
      for {
        event <- Event.ircToEvent(Message(Command.Cap, Vector("*", "ACK", "")))
      } yield assert(event, equalTo(CapabilityAck(List(""))))
    },
    testM("Recognize ACCOUNT accountName (logged in msg)") {
      val prefix = Prefix("nick", "user", "host")
      for {
        event <- Event.ircToEvent(
                  Message(Command.Account, Vector("accountName"), Some(prefix))
                )
      } yield assert(event, equalTo(UserLoggedIn(prefix.nick, "accountName")))
    },
    testM("Recognize ACCOUNT * msg (logged out)") {
      val prefix = Prefix("nick", "user", "host")
      for {
        event <- Event.ircToEvent(
                  Message(Command.Account, Vector("*"), Some(prefix))
                )
      } yield assert(event, equalTo(UserLoggedOut(prefix.nick)))
    },
    testM("Recognize Join msg when its extended join capability") {
      val prefix = Prefix("nick", "user", "host")
      val msg    = Message(Command.Join, Vector("#votbot", "*", ""), Some(prefix))
      for {
        _     <- ZIO.accessM[BotState](_.botState.addCapabilities(Capabilities.ExtendedJoin))
        event <- Event.ircToEvent(msg)
      } yield assert(event, equalTo(ExtendedJoin("nick", "#votbot", "*")))
    }
  )
}
