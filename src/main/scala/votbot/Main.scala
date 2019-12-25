package votbot

import java.nio.file.Paths

import votbot.event.Event._
import votbot.event.handlers.{ BaseEventHandler, DefaultEventHandler, Help }
import votbot.event.{ Event, EventHandler }
import votbot.model.Bot.State
import votbot.model.irc._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{ Console, _ }
import zio.random.Random
import zio.system
import pureconfig._
import pureconfig.generic.auto._
import votbot.database.{
  ChannelHandlersRepo,
  ChannelSettingsRepo,
  DatabaseProvider,
  QuotesRepo,
  SqliteChannelHandlersRepo,
  SqliteChannelSettingsRepo,
  SqliteDatabaseProvider,
  SqliteQuotesRepo
}
import votbot.event.handlers.ultimatequotes.UltimateQuotes

object Main extends App {

  trait BaseEnv extends Console.Live with Clock.Live with Random.Live with Blocking.Live

  trait VotbotEnv
      extends Console
      with Clock
      with Random
      with Configuration
      with BotState
      with Api
      with BaseEventHandler
      with Blocking
      with HttpClient
      with DatabaseProvider
      with QuotesRepo
      with ChannelSettingsRepo
      with ChannelHandlersRepo

  private def mkCfgPath() =
    system
      .property("user.dir")
      .map(_.getOrElse(".") + "/" + "application.conf")
      .map(Paths.get(_))

  private def mkEnvironment: ZIO[ZEnv, Serializable, VotbotEnv] =
    for {
      cfgPath <- mkCfgPath()
      cfg <- ZIO
              .fromEither(ConfigSource.file(cfgPath).load[Config])
              .orElse(ZIO.fromEither(ConfigSource.default.load[Config]))
      st       <- Ref.make(State(cfg.bot.nick))
      inQ      <- Queue.unbounded[String]
      outQ     <- Queue.unbounded[Message]
      pQ       <- Queue.unbounded[Message]
      evtQ     <- Queue.unbounded[Event]
      chs      <- Ref.make(Map.empty[ChannelKey, Channel])
      handlers <- Ref.make(Set[EventHandler[Event]](Help, UltimateQuotes))
      users    <- Ref.make(Map.empty[UserKey, User])
    } yield new VotbotEnv
      with BaseEnv
      with DefaultEventHandler
      with SqliteDatabaseProvider
      with SqliteQuotesRepo
      with SqliteChannelSettingsRepo
      with SqliteChannelHandlersRepo
      with DefaultHttpClient {

      override val customHandlers: Ref[Set[EventHandler[Event]]] = handlers

      override val configuration: Configuration.Service[Any] = new Configuration.Service[Any] {
        override val config: Config = cfg
        override val http: Http     = cfg.http
        override val server: Server = cfg.server
        override val admin: Admin   = cfg.admin
        override val bot: BotProps  = cfg.bot
      }

      override val botState: BotState.Service[Any] = new BotStateLive[Any] {
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
    }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    mainLogic(args)
      .provideSomeM(mkEnvironment)
      .either
      .map(_.fold(e => { println(e); 1 }, _ => 0))

  def mainLogic(args: List[String]): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      qsRepo <- ZIO.access[QuotesRepo](_.quotesRepo)
      csRepo <- ZIO.access[ChannelSettingsRepo](_.channelSettingsRepo)
      chRepo <- ZIO.access[ChannelHandlersRepo](_.channelHandlersRepo)
      _ <- ZIO
            .when(args.contains("--initdb")) {
              qsRepo.createSchemaIfNotExists *>
                csRepo.createSchemaIfNotExists *>
                chRepo.createSchemaIfNotExists
            }
            .catchAll(dbe => ZIO.fail(dbe.throwable))
      client         <- Client.make().fork
      parser         <- MsgParser.parser().forever.fork
      processor      <- processor().forever.fork
      evtProcessor   <- Event.eventProcessor().forever.fork
      _              <- parser.zip(processor).await
      _              <- evtProcessor.await
      consoleControl <- ConsoleControl().fork
      _              <- client.await
    } yield ()

  def processor(): ZIO[Api with BotState with Console, Throwable, Unit] =
    for {
      api <- ZIO.access[Api](_.api)
      msg <- api.dequeueParsedMessage()
      _   <- putStrLn("Processing IRC Message: " + msg.toString)
      evt <- Event.ircToEvent(msg)
      _   <- api.enqueueEvent(evt)
    } yield ()

}
