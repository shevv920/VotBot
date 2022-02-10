package votbot.model.irc

import java.nio.charset.StandardCharsets

import votbot.IrcMessageParser.{ messageDelimiter, separator }
import zio.{ UIO, ZIO }

final case class Message(cmd: Command, args: List[String], prefix: Option[Prefix] = None)

object Message {

  def apply(cmd: Command, args: String*): Message =
    if (args.size > 1) //always add ":" to last param
      new Message(cmd, args.toList.dropRight(1) :+ ":" + args.last, None)
    else
      new Message(cmd, args.toList, None)

  def toByteArray(msg: Message): UIO[Array[Byte]] =
    ZIO.succeed {
      val str =
        msg.cmd.entryName.toUpperCase() +
          separator +
          msg.args.mkString(separator) +
          messageDelimiter
      str.getBytes(StandardCharsets.UTF_8)
    }
}
