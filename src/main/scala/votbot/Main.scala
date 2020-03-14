package votbot

import votbot.Api.Api
import votbot.BotState.BotState
import votbot.Configuration.Configuration
import votbot.HttpClient.HttpClient
import votbot.database.Database
import votbot.database.Database.Database
import votbot.event.EventHandler.EventHandler
import votbot.event.{ Event, EventHandler }
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{ Console, _ }
import zio.random.Random
import zio.system.System

object Main extends App {

  type VotbotEnv = Any
    with Console
    with Clock
    with Random
    with Configuration
    with BotState
    with Api
    with Blocking
    with HttpClient
    with Database

  private val system   = System.live
  private val clock    = Clock.live
  private val blocking = Blocking.live
  private val console  = Console.live
  private val random   = Random.live

  private val config     = system >>> Configuration.defaultConfig
  private val botState   = config >>> BotState.defaultBotState
  private val httpClient = config >>> HttpClient.defaultHttpClient
  private val api        = Api.defaultApi
  private val database   = Database.defaultDatabase

  private val votBotEnv = console ++
    blocking ++
    clock ++
    random ++
    system ++
    config ++
    botState ++
    api ++
    httpClient ++
    database
  private val eventHandler = (api ++ config ++ database ++ botState ++ random) >>> EventHandler.defaultEventHandler

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    mainLogic(args)
      .provideCustomLayer(votBotEnv ++ eventHandler)
      .either
      .map(_.fold(e => { println(e); 1 }, _ => 0))

  def mainLogic(args: List[String]): ZIO[VotbotEnv with EventHandler, Throwable, Unit] =
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

  def eventProcessor(): ZIO[VotbotEnv with EventHandler, Throwable, Unit] =
    for {
      evt <- Api.dequeueEvent()
      _   <- putStrLn("Processing Event: " + evt.toString)
      _   <- EventHandler.handle(evt)
    } yield ()

  def messageProcessor(): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      msg <- Api.dequeueParsedMessage()
      evt <- Event.fromIrcMessage(msg)
      _   <- Api.enqueueEvent(evt)
    } yield ()
}
