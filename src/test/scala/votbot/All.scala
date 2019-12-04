package votbot

import votbot.event.Event.Event
import votbot.event.handlers.BaseEventHandler
import votbot.event.{ BaseEventHandlerSpec, Event, EventHandler, EventSpec }
import votbot.model.Bot.State
import votbot.model.irc.{ Channel, ChannelKey, RawMessage, User, UserKey }
import zio.blocking.Blocking
import zio.console._
import zio.random.Random
import zio.test._
import zio.{ Queue, Ref }

object Base {

  val env = for {
    inQ      <- Queue.unbounded[String]
    outQ     <- Queue.unbounded[RawMessage]
    pQ       <- Queue.unbounded[RawMessage]
    evtQ     <- Queue.unbounded[Event]
    chs      <- Ref.make(Map.empty[ChannelKey, Channel])
    users    <- Ref.make(Map.empty[UserKey, User])
    handlers <- Ref.make(List.empty[EventHandler])
    st       <- Ref.make(State("votbot"))
  } yield new Api
    with Console.Live
    with TestConfiguration
    with BotState
    with BaseEventHandler
    with Random.Live
    with Blocking.Live
    with TestDatabase {

    override val api = new DefaultApi[Any] {
      override val parseQ: Queue[String]                        = inQ
      override val processQ: Queue[RawMessage]                  = pQ
      override val outMessageQ: Queue[RawMessage]               = outQ
      override val eventQ: Queue[Event.Event]                   = evtQ
      override val knownChannels: Ref[Map[ChannelKey, Channel]] = chs
      override val knownUsers: Ref[Map[UserKey, User]]          = users
    }
    override val customHandlers: Ref[List[EventHandler]] = handlers

    override val state: BotState.Service[Any] = new BotStateLive[Any] {
      override val state: Ref[State] = st
    }
  }

  val envM = env.toManaged_

}

object All
    extends DefaultRunnableSpec(
      suite("all")(
        MsgParserSpec.tests,
        ApiSpec.tests,
        BaseEventHandlerSpec.tests,
        DatabaseSpec.tests,
        EventSpec.tests
      ).provideManaged(Base.envM)
    )
