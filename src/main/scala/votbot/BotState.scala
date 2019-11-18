package votbot

import votbot.model.Bot.State
import zio.Ref

trait BotState {
  val state: Ref[State]
}
