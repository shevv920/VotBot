package votbot.event

import votbot.BotState
import votbot.event.Event.{ CapabilityAck, ExtendedJoin, UserLoggedIn, UserLoggedOut }
import votbot.model.irc.{ Capabilities, ChannelKey, Command, Message, Prefix, UserKey }
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.{ assert, suite, testM }

object EventSpec {

  val tests = suite("Event ")(
    testM("Recognize CAP * ACK msg") {
      for {
        event <- Event.fromIrcMessage(Message(Command.Cap, List("*", "ACK", "cap1 cap2 cap3")))
      } yield assert(event, equalTo(CapabilityAck(List("cap1 cap2 cap3".split("\\s"): _*))))
    },
    testM("Recognize CAP * ACK : msg (empty caps list)") {
      for {
        event <- Event.fromIrcMessage(Message(Command.Cap, List("*", "ACK", "")))
      } yield assert(event, equalTo(CapabilityAck(List(""))))
    },
    testM("Recognize ACCOUNT accountName (logged in msg)") {
      val prefix = Prefix("nick", "user", "host")
      for {
        event <- Event.fromIrcMessage(
                  Message(Command.Account, List("accountName"), Some(prefix))
                )
      } yield assert(event, equalTo(UserLoggedIn(UserKey(prefix.nick), "accountName")))
    },
    testM("Recognize ACCOUNT * msg (logged out)") {
      val prefix = Prefix("nick", "user", "host")
      for {
        event <- Event.fromIrcMessage(
                  Message(Command.Account, List("*"), Some(prefix))
                )
      } yield assert(event, equalTo(UserLoggedOut(UserKey(prefix.nick))))
    },
    testM("Recognize Join msg when its extended join capability") {
      val prefix = Prefix("nick", "user", "host")
      val msg    = Message(Command.Join, List("#votbot", "*", ""), Some(prefix))
      for {
        _     <- ZIO.accessM[BotState](_.botState.addCapabilities(Capabilities.ExtendedJoin))
        event <- Event.fromIrcMessage(msg)
      } yield assert(event, equalTo(ExtendedJoin(UserKey("nick"), ChannelKey("#votbot"), "*")))
    }
  )
}
