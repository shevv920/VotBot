package votbot

import votbot.model.irc.{Command, Message, NumericCommand, Prefix}
import zio.test.Assertion.equalTo
import zio.test.{assert, suite, test}

object MsgParserSpec {

  val tests = suite("MsgParser")(
    test("should parse simple PRIVMSG") {
      IrcMessageParser
        .parse("PRIVMSG votbot message")
        .map(m => assert(m)(equalTo(Message(Command.Privmsg, List("votbot", "message")))))
    },
    test("should parse numeric msg") {
      IrcMessageParser
        .parse(NumericCommand.RPL_WELCOME + " welcome")
        .map(m => assert(m)(equalTo(Message(Command.Numeric(NumericCommand.RPL_WELCOME), List("welcome")))))
    },
    test("should parse CAP * ACK msg") {
      IrcMessageParser
        .parse("CAP * ACK :cap1 cap2 cap3")
        .map(m => assert(m)(equalTo(Message(Command.Cap, List("*", "ACK", "cap1 cap2 cap3")))))
    },
    test("should parse CAP * ACK : (empty caps list)") {
      IrcMessageParser
        .parse("CAP * ACK :")
        .map(m => assert(m)(equalTo(Message(Command.Cap, List("*", "ACK", "")))))
    },
    test("should parse ACCOUNT commands") {
      IrcMessageParser
        .parse(":nick!user@host ACCOUNT accountName")
        .map(m =>
          assert(m)(equalTo(Message(Command.Account, List("accountName"), Some(Prefix("nick", "user", "host")))))
        )
    }
  )
}
