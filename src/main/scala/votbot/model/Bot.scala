package votbot.model

import votbot.model.irc.Capability

object Bot {
  final case class State(nick: String, capabilities: Set[Capability] = Set.empty)
}
