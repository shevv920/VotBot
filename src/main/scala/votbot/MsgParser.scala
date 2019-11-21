package votbot

import java.nio.charset.StandardCharsets

import votbot.model.Irc.Command.Unknown
import votbot.model.Irc.{Command, Prefix, RawMessage}
import zio.console._
import zio.{Task, UIO, ZIO}

import scala.annotation.tailrec
import scala.util.matching.Regex

object MsgParser {
  val separator        = " "
  val messageDelimiter = "\r\n"
  //final val prefixRegexp = """(?i):([a-zA-Z0-9\[\]\\\-`{}^]+)!(\w+)@(.*)""".r
  val userPrefix: Regex = """:(.*)!(.*)@(.*)""".r
  val hostPrefix: Regex = """:(.*) """.r

  def parse(raw: String): Task[RawMessage] =
    ZIO.effect {
      val (prefix, commandParams) =
        if (raw.startsWith(":"))
          raw.splitAt(raw.indexOf(' ') + 1)
        else ("", raw)
      val (command, params) =
        if (commandParams.indexOf(' ') == -1) (commandParams, "")
        else commandParams.splitAt(commandParams.indexOf(' '))
      val cmd =
        if (command.forall(_.isDigit))
          Command.Numeric(command)
        else
          Command.withNameOption(command.toUpperCase).getOrElse(Unknown(command))
      RawMessage(cmd, parseParams(params.trim), parsePrefix(prefix))
    }

  private def parsePrefix(prefix: String): Option[Prefix] = prefix match {
    case userPrefix(nick, user, host) =>
      Some(Prefix(nick, user, host.trim))
    case hostPrefix(host) =>
      Some(Prefix("", "", host))
    case _ => None
  }

  @tailrec
  private def parseParams(remaining: String, cur: Vector[String] = Vector.empty): Vector[String] =
    if (remaining.isEmpty) cur
    else if (remaining.startsWith(":")) { //remaining - last param
      cur :+ remaining.drop(1)
    } else {
      val split        = remaining.split(" ", 2)
      val (param, rem) = (split(0), if (split.length > 1) split(1) else "")
      parseParams(rem, cur :+ param)
    }

  def parser(): ZIO[Api, Throwable, Unit] =
    for {
      api        <- ZIO.environment[Api]
      raw        <- api.dequeueParse()
      _          <- putStrLn("got to parse: " + raw).provide(Console.Live)
      ircMessage <- parse(raw)
      _          <- api.enqueueProcess(ircMessage)
    } yield ()

  def msgToByteArray(msg: RawMessage): UIO[Array[Byte]] =
    ZIO.effectTotal {
      val str =
        separator +
          msg.cmd.entryName.toUpperCase() +
          separator +
          msg.args.mkString(separator) +
          messageDelimiter
      str.getBytes(StandardCharsets.UTF_8)
    }
}
