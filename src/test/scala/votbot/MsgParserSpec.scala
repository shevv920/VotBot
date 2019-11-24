package votbot
import votbot.model.Irc
import zio.test.Assertion.equalTo
import zio.test.{ assert, suite, testM }

object MsgParserSpec {

  val tests = suite("MsgParser")(
    testM("should parse simple PRIVMSG") {
      MsgParser
        .parse("PRIVMSG votbot message")
        .map(m => assert(m, equalTo(Irc.RawMessage(Irc.Command.Privmsg, Vector("votbot", "message")))))
    },
    testM("should parse numeric msg") {
      MsgParser
        .parse(Irc.Numeric.RPL_WELCOME + " welcome")
        .map(m => assert(m, equalTo(Irc.RawMessage(Irc.Command.Numeric(Irc.Numeric.RPL_WELCOME), Vector("welcome")))))
    }
  )
}
