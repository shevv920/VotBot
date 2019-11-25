package votbot

import votbot.event.Event.Event
import votbot.event.handlers.BaseEventHandler
import votbot.event.{BaseEventHandlerSpec, Event, EventHandler}
import votbot.model.Bot.State
import votbot.model.Irc
import votbot.model.Irc.{Channel, RawMessage, User}
import zio.blocking.Blocking
import zio.console._
import zio.random.Random
import zio.test._
import zio.{Queue, Ref}

object Base {

  val env = for {
    inQ      <- Queue.unbounded[String]
    outQ     <- Queue.unbounded[RawMessage]
    pQ       <- Queue.unbounded[RawMessage]
    evtQ     <- Queue.unbounded[Event]
    chs      <- Ref.make(Map.empty[String, Channel])
    users    <- Ref.make(Map.empty[String, User])
    handlers <- Ref.make(List.empty[EventHandler])
    st       <- Ref.make(State("votbot"))
  } yield new LiveApi with Console.Live with TestConfiguration with BotState with BaseEventHandler with Random.Live
  with Blocking.Live {
    override val parseQ: Queue[String]                   = inQ
    override val processQ: Queue[RawMessage]             = pQ
    override val outMessageQ: Queue[RawMessage]          = outQ
    override val eventQ: Queue[Event.Event]              = evtQ
    override val knownChannels: Ref[Map[String, Irc.Channel]] = chs
    override val knownUsers: Ref[Map[String, Irc.User]]  = users
    override val customHandlers: Ref[List[EventHandler]] = handlers
    override val state: Ref[State]                       = st
  }

  val envM = env.toManaged_

}

object All
    extends DefaultRunnableSpec(
      suite("all")(
        MsgParserSpec.tests,
        ApiSpec.tests,
        BaseEventHandlerSpec.tests
      ).provideManaged(Base.envM)
    )
