package votbot

import votbot.database.Database
import votbot.event.{ EventHandler }

import zio.Clock
import zio.System
import zio.Console
import zio.Random
import zio.test._
import zio.test.ZIOSpecDefault

object All {

  private val system   = System.live
  private val clock    = Clock.live
  private val console  = Console.live
  private val random   = Random.live

  private val config     = system >>> Configuration.defaultConfig
  private val botState   = config >>> BotState.defaultBotState
  private val httpClient = config >>> HttpClient.defaultHttpClient
  val api                = Api.defaultApi
  private val database   = Database.defaultDatabase

  val votBotEnv = console ++
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

object AllSuites extends ZIOSpecDefault {
  def spec = suite("All tests")(ApiSpec.tests, MsgParserSpec.tests).provideLayer(All.api)
}
