package votbot.model

import votbot.model.irc.Capabilities.Capability

object Bot {
  final case class State(nick: String, capabilities: Set[Capability] = Set.empty)
}
