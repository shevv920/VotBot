package votbot.event

import votbot.event.Event.Event
import votbot.event.handlers.BaseEventHandler
import votbot.{ Api, BotState, Configuration, Database, HttpClient }
import zio.ZIO
import zio.blocking.Blocking
import zio.console.Console
import zio.random.Random

trait EventHandler {

  type HandlerEnv = Api
    with Configuration
    with BotState
    with Random
    with BaseEventHandler
    with Blocking
    with HttpClient
    with Database
    with Console
  def handle(event: Event): ZIO[HandlerEnv, Throwable, Unit]
}
