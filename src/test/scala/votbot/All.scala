package votbot

import java.nio.charset.StandardCharsets

import votbot.event.Event.Event
import votbot.event.handlers.BaseEventHandler
import votbot.event.{ BaseEventHandlerSpec, Event, EventHandler }
import votbot.model.Bot.State
import votbot.model.Irc
import votbot.model.Irc.{ Channel, RawMessage, User }
import zio.blocking.Blocking
import zio.console._
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.{ Queue, Ref, ZIO }

object Base {

  val env = for {
    inQ      <- Queue.unbounded[String]
    outQ     <- Queue.unbounded[RawMessage]
    pQ       <- Queue.unbounded[RawMessage]
    evtQ     <- Queue.unbounded[Event]
    chs      <- Ref.make(Map.empty[String, Channel])
    users    <- Ref.make(Set.empty[User])
    handlers <- Ref.make(List.empty[EventHandler])
    st       <- Ref.make(State("votbot"))
  } yield new LiveApi with Console.Live with TestConfiguration with BotState with BaseEventHandler with Random.Live
  with Blocking.Live {
    override val parseQ: Queue[String]                   = inQ
    override val processQ: Queue[RawMessage]             = pQ
    override val outMessageQ: Queue[RawMessage]          = outQ
    override val eventQ: Queue[Event.Event]              = evtQ
    override val channels: Ref[Map[String, Irc.Channel]] = chs
    override val knownUsers: Ref[Set[Irc.User]]          = users
    override val customHandlers: Ref[List[EventHandler]] = handlers
    override val state: Ref[State]                       = st
  }

  val envM = env.toManaged_

}

object All
    extends DefaultRunnableSpec(
      suite("all")(
        MsgParserSpec.tests,
        BaseEventHandlerSpec.tests,
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
