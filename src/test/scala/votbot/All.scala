package votbot

import votbot.Main.{ BaseEnv, VotbotEnv }
import votbot.database.{
  SqliteChannelHandlersRepo,
  SqliteChannelSettingsRepo,
  SqliteDatabaseProvider,
  SqliteQuotesRepo
}
import votbot.event.Event.Event
import votbot.event.handlers.{ DefaultCustomHandlers, DefaultEventHandler }
import votbot.event.{ BaseEventHandlerSpec, Event, EventHandler, EventSpec }
import votbot.model.Bot.State
import votbot.model.irc._
import zio.blocking.Blocking
import zio.test._
import zio.{ Queue, Ref }

object Base {

  val env = for {
    inQ   <- Queue.unbounded[String]
    outQ  <- Queue.unbounded[Message]
    pQ    <- Queue.unbounded[Message]
    evtQ  <- Queue.unbounded[Event]
    chs   <- Ref.make(Map.empty[ChannelKey, Channel])
    users <- Ref.make(Map.empty[UserKey, User])
    hs    <- Ref.make(Set.empty[EventHandler[Event]])
    st    <- Ref.make(State("votbot"))
  } yield new VotbotEnv
    with TestConfiguration
    with BaseEnv
    with SqliteDatabaseProvider
    with SqliteChannelSettingsRepo
    with SqliteChannelHandlersRepo
    with SqliteQuotesRepo
    with DefaultEventHandler
    with DefaultCustomHandlers
    with DefaultHttpClient
    with Blocking.Live {

    override val api = new DefaultApi[Any] {
      override val receivedQ: Queue[String]                     = inQ
      override val parsedMessageQ: Queue[Message]               = pQ
      override val outMessageQ: Queue[Message]                  = outQ
      override val eventQ: Queue[Event.Event]                   = evtQ
      override val knownChannels: Ref[Map[ChannelKey, Channel]] = chs
      override val knownUsers: Ref[Map[UserKey, User]]          = users
    }
    override val handlers: Ref[Set[EventHandler[Event]]] = hs

    override val botState: BotState.Service[Any] = new BotStateLive[Any] {
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
