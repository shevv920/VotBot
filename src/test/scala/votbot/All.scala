package votbot

import votbot.database.Database
import votbot.event.EventHandler
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.random.Random
import zio.system.System

object All {

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

  val votBotEnv = console ++
    blocking ++
    clock ++
    random ++
    system ++
    config ++
    botState ++
    api ++
    httpClient ++
    database
  val eventHandler = (api ++ config ++ database ++ botState ++ random) >>> EventHandler.defaultEventHandler

}
