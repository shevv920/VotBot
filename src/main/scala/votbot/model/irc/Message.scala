package votbot.model.irc

final case class Message(cmd: Command, args: Vector[String], prefix: Option[Prefix] = None)

object Message {

  def apply(cmd: Command, args: String*): Message =
    if (args.size > 1) //always add ":" to last param if many
      new Message(cmd, args.toVector.updated(args.size - 1, ":" + args.last), None)
    else
      new Message(cmd, args.toVector, None)
}
