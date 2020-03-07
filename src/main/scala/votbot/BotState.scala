package votbot

import votbot.Configuration.Configuration
import votbot.model.Bot.State
import votbot.model.irc.Capability
import zio.{ Has, IO, Ref, Task, ZIO, ZLayer }

object BotState {
  type BotState = Has[BotState.Service]

  trait Service {
    protected val state: Ref[State]
    def currentNick(): IO[Nothing, String]
    def currentCapabilities(): IO[Nothing, Set[Capability]]
    def setNick(nick: String): Task[Unit]
    def addCapabilities(caps: Capability*): Task[Unit]
    def removeCapabilities(caps: Capability*): Task[Unit]
    def isCapabilityEnabled(cap: Capability): Task[Boolean]
  }

  val defaultBotState: ZLayer[Configuration, Nothing, BotState] = ZLayer.fromEffect(
    for {
      bot <- Configuration.bot
      st  <- Ref.make(State(bot.nick))
    } yield new DefaultBotState {
      override protected val state: Ref[State] = st
    }
  )
}

trait DefaultBotState extends BotState.Service {
  override def currentNick(): IO[Nothing, String]                  = state.get.map(_.nick)
  override def currentCapabilities(): IO[Nothing, Set[Capability]] = state.get.map(_.capabilities)
  override def setNick(nick: String): Task[Unit]                   = state.update(s => s.copy(nick = nick)).unit
  override def isCapabilityEnabled(cap: Capability): Task[Boolean] = state.get.map(_.capabilities.contains(cap))

  override def addCapabilities(caps: Capability*): Task[Unit] =
    state.update(s => s.copy(capabilities = s.capabilities ++ caps)).unit

  override def removeCapabilities(caps: Capability*): Task[Unit] =
    state.update(s => s.copy(capabilities = s.capabilities -- caps)).unit
}
