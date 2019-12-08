package votbot

import votbot.model.Bot.State
import votbot.model.irc.Capability
import zio.{ Ref, Task, ZIO }

trait BotState {
  val state: BotState.Service[Any]
}

object BotState {

  trait Service[R] {
    protected val state: Ref[State]
    def currentNick(): ZIO[R, Nothing, String]
    def currentCapabilities(): ZIO[R, Nothing, Set[Capability]]
    def setNick(nick: String): Task[Unit]
    def addCapabilities(caps: Capability*): Task[Unit]
    def removeCapabilities(caps: Capability*): Task[Unit]
    def isCapabilityEnabled(cap: Capability): Task[Boolean]
  }
}

trait BotStateLive[R] extends BotState.Service[R] {
  override def currentNick(): ZIO[R, Nothing, String]                  = state.get.map(_.nick)
  override def currentCapabilities(): ZIO[R, Nothing, Set[Capability]] = state.get.map(_.capabilities)
  override def setNick(nick: String): Task[Unit]                       = state.update(s => s.copy(nick = nick)).unit
  override def isCapabilityEnabled(cap: Capability): Task[Boolean]     = state.get.map(_.capabilities.contains(cap))

  override def addCapabilities(caps: Capability*): Task[Unit] =
    state.update(s => s.copy(capabilities = s.capabilities ++ caps)).unit

  override def removeCapabilities(caps: Capability*): Task[Unit] =
    state.update(s => s.copy(capabilities = s.capabilities -- caps)).unit
}
