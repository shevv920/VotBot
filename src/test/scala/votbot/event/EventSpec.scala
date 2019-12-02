package votbot.event
import votbot.event.Event.CapabilityAck
import votbot.model.irc.{ Command, RawMessage }
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
    }
  )
}
