package votbot.model.irc

import votbot.event.Event

final case class Channel(
  name: String,
  modes: List[ChannelMode],
  members: Set[UserKey],
  handlers: Set[Event.Handler],
  handleFunction: Event.HandleFunction
)
