package votbot

import java.nio.charset.StandardCharsets

import votbot.event.Event
import votbot.event.Event.Event
import votbot.model.Irc
import votbot.model.Irc.{ Channel, RawMessage, User }
import zio.console._
import zio.test.Assertion._
import zio.test._
import zio.{ Managed, Queue, Ref, ZIO }

object Base {

  val env: ZIO[Any, Nothing, LiveApi with Console.Live] = for {
    inQ   <- Queue.unbounded[String]
    outQ  <- Queue.unbounded[RawMessage]
    pQ    <- Queue.unbounded[RawMessage]
    evtQ  <- Queue.unbounded[Event]
    chs   <- Ref.make(Map.empty[String, Channel])
    users <- Ref.make(Set.empty[User])
  } yield new LiveApi with Console.Live {
    override protected val parseQ: Queue[String]                   = inQ
    override protected val processQ: Queue[RawMessage]             = pQ
    override protected val outMessageQ: Queue[RawMessage]          = outQ
    override protected val eventQ: Queue[Event.Event]              = evtQ
    override protected val channels: Ref[Map[String, Irc.Channel]] = chs
    override protected val knownUsers: Ref[Set[Irc.User]]          = users
  }
  val envM: Managed[Nothing, Api with Console] = env.toManaged_

}

object All
    extends DefaultRunnableSpec(
      suite("all")(
        testM("MsgParser should parse simple PRIVMSG") {
          val msg = "PRIVMSG votbot message"
          MsgParser
            .parse(msg)
            .map(m => assert(m, equalTo(Irc.RawMessage(Irc.Command.Privmsg, Vector("votbot", "message")))))
        },
        testM("test1") {
          val msg = "test message"
          for {
            api      <- ZIO.environment[Api]
            _        <- api.sendPrivateMessage("votbot", msg)
            oMsg     <- api.dequeueOutMessage()
            ba       <- MsgParser.msgToByteArray(oMsg)
            sMsg     = new String(ba, StandardCharsets.UTF_8)
            _        <- api.enqueueParse(sMsg)
            _        <- MsgParser.parser()
            parseRes <- api.dequeueProcess()
          } yield assert(parseRes.cmd, equalTo(Irc.Command.Privmsg)) &&
            assert(parseRes.prefix, isNone) &&
            assert(parseRes.args.head, equalTo("votbot"))
        }
      ).provideManaged(Base.envM)
    )
