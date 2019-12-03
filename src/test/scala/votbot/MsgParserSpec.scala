package votbot

import votbot.model.irc.{ Command, NumericCommand, Prefix, RawMessage }
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
    },
    testM("should parse CAP * ACK : (empty caps list)") {
      MsgParser
        .parse("CAP * ACK :")
        .map(m => assert(m, equalTo(RawMessage(Command.Cap, Vector("*", "ACK", "")))))
    },
    testM("should parse ACCOUNT commands") {
      MsgParser
        .parse(":nick!user@host ACCOUNT accountName")
        .map(m =>
          assert(m, equalTo(RawMessage(Command.Account, Vector("accountName"), Some(Prefix("nick", "user", "host")))))
        )
    }
  )
}
