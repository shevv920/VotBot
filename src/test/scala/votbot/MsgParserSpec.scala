package votbot

import votbot.model.irc.{ Command, NumericCommand, RawMessage }
import zio.test.Assertion.equalTo
import zio.test.{ assert, suite, testM }

object MsgParserSpec {

  val tests = suite("MsgParser")(
    testM("should parse simple PRIVMSG") {
      MsgParser
        .parse("PRIVMSG votbot message")
        .map(m => assert(m, equalTo(RawMessage(Command.Privmsg, Vector("votbot", "message")))))
    },
    testM("should parse numeric msg") {
      MsgParser
        .parse(NumericCommand.RPL_WELCOME + " welcome")
        .map(m => assert(m, equalTo(RawMessage(Command.Numeric(NumericCommand.RPL_WELCOME), Vector("welcome")))))
    },
    testM("should parse CAP * ACK msg") {
      MsgParser
        .parse("CAP * ACK :cap1 cap2 cap3")
        .map(m => assert(m, equalTo(RawMessage(Command.Cap, Vector("*", "ACK", "cap1 cap2 cap3")))))
    }
  )
}
