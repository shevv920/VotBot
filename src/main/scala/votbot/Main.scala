package votbot

import votbot.Api.Api
import votbot.BotState.BotState
import votbot.Configuration.Configuration
import votbot.HttpClient.HttpClient
import votbot.database.Database
import votbot.database.Database.Database
import votbot.event.EventHandler.EventHandler
import votbot.event.{ Event, EventHandler }
import zio.ZLayer.NoDeps
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{ Console, _ }
import zio.random.Random
import zio.system.System

object Main extends App {

  val baseLayer = Console.live ++
    Clock.live ++
    Blocking.live ++
    Random.live ++
    System.live

  type VotbotEnv = Any
    with Console
    with Clock
    with Random
    with Configuration
    with BotState
    with Api
    with EventHandler
    with Blocking
    with HttpClient
    with Database

  val config: ZLayer[Any, Any, Configuration]  = baseLayer >>> Configuration.defaultConfig
  val botState: ZLayer[Any, Any, BotState]     = config >>> BotState.defaultBotState
  val api: NoDeps[Nothing, Api]                = Api.defaultApi
  val httpClient: ZLayer[Any, Any, HttpClient] = config >>> HttpClient.defaultHttpClient
  val database: NoDeps[Nothing, Database]      = Database.defaultDatabase

  val eventHandler = (baseLayer ++ config ++ botState ++ api ++ httpClient ++ database) >>> EventHandler.defaultEventHandler
  val votBotEnv    = baseLayer ++ config ++ botState ++ api ++ httpClient ++ database ++ eventHandler

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    mainLogic(args)
      .provideCustomLayer(votBotEnv)
      .either
      .map(_.fold(e => { println(e); 1 }, _ => 0))

  def mainLogic(args: List[String]): ZIO[VotbotEnv, Serializable, Unit] =
    for {
      client         <- Client.make().fork
      parser         <- IrcMessageParser.parser().forever.fork
      msgProcessor   <- messageProcessor().forever.fork
      evtProcessor   <- eventProcessor().forever.fork
      consoleControl <- ConsoleControl().forever.fork
      _              <- client.await
      _              <- parser.interrupt
      _              <- evtProcessor.interrupt
      _              <- msgProcessor.interrupt
      _              <- consoleControl.interrupt
    } yield ()

  def eventProcessor(): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      api     <- ZIO.access[Api](_.get)
      evt     <- api.dequeueEvent()
      handler <- ZIO.access[EventHandler](_.get)
      _       <- putStrLn("Processing Event: " + evt.toString)
      _       <- handler.handle(evt)
    } yield ()

  def messageProcessor(): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      api <- ZIO.access[Api](_.get)
      msg <- api.dequeueParsedMessage()
      evt <- Event.fromIrcMessage(msg)
      _   <- api.enqueueEvent(evt)
    } yield ()
}
