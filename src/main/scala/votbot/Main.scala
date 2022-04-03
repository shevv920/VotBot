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

import zio.Clock
import zio.{ Console, Random, ZIOAppDefault }
import zio.Console.printLine

object Main extends ZIOAppDefault {

  type VotbotEnv = Any
    with Console
    with Clock
    with Random
    with Configuration
    with BotState
    with Api
    with Any
    with HttpClient
    with Database

  private val clock   = Clock.live
  private val console = Console.live
  private val random  = Random.live

  private val config     = Configuration.defaultConfig
  private val botState   = config >>> BotState.defaultBotState
  private val httpClient = config >>> HttpClient.defaultHttpClient
  private val api        = Api.defaultApi
  private val database   = Database.defaultDatabase

  private val votBotEnv = console ++
    clock ++
    random ++
    config ++
    botState ++
    api ++
    httpClient ++
    database
  private val eventHandler = (api ++ config ++ database ++ botState ++ random) >>> EventHandler.defaultEventHandler

  override def run =
    mainLogic
      .provideLayer(votBotEnv ++ eventHandler)
      .either
      .map(_.fold(e => { println(e); ExitCode.failure }, _ => ExitCode.success))

  def mainLogic: ZIO[VotbotEnv with EventHandler, Throwable, Unit] =
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
      _   <- printLine("Processing Event: " + evt.toString)
      _   <- EventHandler.handle(evt)
    } yield ()

  def messageProcessor(): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      msg <- Api.dequeueParsedMessage()
      evt <- Event.fromIrcMessage(msg)
      _   <- Api.enqueueEvent(evt)
    } yield ()
}
