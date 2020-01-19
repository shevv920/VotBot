package votbot

import java.nio.file.Paths

import votbot.event.{ CustomHandlers, DefaultCustomHandlers, DefaultEventHandler, Event, EventHandler }
import votbot.model.Bot.State
import votbot.model.irc._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{ Console, _ }
import zio.random.Random
import zio.system
import pureconfig._
import votbot.database.{ Database, DefaultDatabase }
import votbot.event.CustomHandlers.Handle
import pureconfig.generic.auto._

object Main extends App {

  trait BaseEnv extends Console.Live with Clock.Live with Random.Live with Blocking.Live

  trait VotbotEnv
      extends Console
      with Clock
      with Random
      with Configuration
      with BotState
      with Api
      with EventHandler
      with Blocking
      with HttpClient
      with Database
      with CustomHandlers

  private def mkCfgPath() =
    system
      .property("user.dir")
      .map(_.getOrElse(".") + "/" + "application.conf")
      .map(Paths.get(_))

  def readConfig() =
    for {
      path <- mkCfgPath()
      cfg <- ZIO
              .fromEither(ConfigSource.file(path).load[Config])
              .orElse(ZIO.fromEither(ConfigSource.default.load[Config]))
    } yield cfg

  private def mkEnvironment: ZIO[ZEnv, Serializable, VotbotEnv] =
    for {
      cfg   <- readConfig()
      st    <- Ref.make(State(cfg.bot.nick))
      inQ   <- Queue.unbounded[String]
      outQ  <- Queue.unbounded[Message]
      pQ    <- Queue.unbounded[Message]
      evtQ  <- Queue.unbounded[Event]
      chs   <- Ref.make(Map.empty[ChannelKey, Channel])
      users <- Ref.make(Map.empty[UserKey, User])
      hs    <- Ref.make(Set.empty[Handle])
      hsf   <- Ref.make[Handle](PartialFunction.empty)
    } yield new VotbotEnv with BaseEnv with DefaultEventHandler with DefaultHttpClient {
      override val database: Database.Service[Any] = new DefaultDatabase

      override val configuration: Configuration.Service[Any] = new Configuration.Service[Any] {
        override val config: Config = cfg
        override val http: Http     = cfg.http
        override val server: Server = cfg.server
        override val admin: Admin   = cfg.admin
        override val bot: BotProps  = cfg.bot
      }

      override val botState: BotState.Service[Any] = new DefaultBotState[Any] {
        override protected val state: Ref[State] = st
      }

      override val api: Api.Service[Any] = new DefaultApi[Any] {
        override protected val receivedQ: Queue[String]                     = inQ
        override protected val parsedMessageQ: Queue[Message]               = pQ
        override protected val outMessageQ: Queue[Message]                  = outQ
        override protected val eventQ: Queue[Event]                         = evtQ
        override protected val knownChannels: Ref[Map[ChannelKey, Channel]] = chs
        override protected val knownUsers: Ref[Map[UserKey, User]]          = users
      }

      override val customHandlers: CustomHandlers.Service[Any] = new DefaultCustomHandlers {
        override val handlers: Ref[Set[Handle]]  = hs
        override val handleFunction: Ref[Handle] = hsf
      }
    }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    mainLogic(args)
      .provideSomeM(mkEnvironment)
      .either
      .map(_.fold(e => { println(e); 1 }, _ => 0))

  def mainLogic(args: List[String]): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      client         <- Client.make().fork
      parser         <- IrcMessageParser.parser().forever.fork
      msgProcessor   <- messageProcessor().forever.fork
      evtProcessor   <- eventProcessor().forever.fork
      consoleControl <- ConsoleControl().fork
//      _              <- CustomHandlers.>.register(DefaultCustomHandlers.helloOnJoin)
      _ <- client.await
      _ <- parser.interrupt
      _ <- evtProcessor.interrupt
      _ <- msgProcessor.interrupt
      _ <- consoleControl.interrupt
    } yield ()

  def eventProcessor(): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      api     <- ZIO.access[Api](_.api)
      evt     <- api.dequeueEvent()
      handler <- ZIO.access[EventHandler](_.eventHandler)
      custom  <- ZIO.access[CustomHandlers](_.customHandlers)
      _       <- putStrLn("Processing Event: " + evt.toString)
      _       <- handler.handle(evt)
      _       <- putStrLn(s"${evt.toString} passed to custom handlers")
      _       <- custom.handle(evt)
    } yield ()

  def messageProcessor(): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      api <- ZIO.access[Api](_.api)
      msg <- api.dequeueParsedMessage()
      evt <- Event.fromIrcMessage(msg)
      _   <- api.enqueueEvent(evt)
    } yield ()
}
