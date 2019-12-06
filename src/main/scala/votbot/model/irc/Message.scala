package votbot.model.irc

sealed trait Message
final case class RawMessage(cmd: Command, args: Vector[String], prefix: Option[Prefix] = None) extends Message

object RawMessage {

  def apply(cmd: Command, args: String*): RawMessage =
    if (args.size > 1)
      new RawMessage(cmd, args.toVector.updated(args.size - 1, ":" + args.last), None)
    else
      new RawMessage(cmd, args.toVector, None)
}
