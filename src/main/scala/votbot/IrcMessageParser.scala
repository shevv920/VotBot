package votbot

import votbot.Api.Api
import votbot.model.irc.Command.Unknown
import votbot.model.irc.{ Command, Message, Prefix }
import zio.console._
import zio.{ Task, ZIO }

import scala.annotation.tailrec
import scala.util.matching.Regex

object IrcMessageParser {
  val separator        = " "
  val messageDelimiter = "\r\n"
  //final val prefixRegexp = """(?i):([a-zA-Z0-9\[\]\\\-`{}^]+)!(\w+)@(.*)""".r
  val userPrefix: Regex          = """:(.*)!(.*)@(.*)""".r
  val hostPrefix: Regex          = """:(.*) """.r
  val numericCommandRegex: Regex = """(\d{3})""".r

  def parse(raw: String): Task[Message] =
    ZIO.effect {
      val (prefix, commandParams) =
        if (raw.startsWith(":"))
          raw.splitAt(raw.indexOf(' ') + 1)
        else ("", raw)
      val (command, params) =
        if (commandParams.indexOf(' ') == -1) (commandParams, "")
        else commandParams.splitAt(commandParams.indexOf(' '))
      val cmd =
        command match {
          case numericCommandRegex(n) => Command.Numeric(n)
          case _                      => Command.withNameOption(command.toUpperCase).getOrElse(Unknown(command))
        }

      Message(cmd, parseParams(params.trim), parsePrefix(prefix))
    }

  private def parsePrefix(prefix: String): Option[Prefix] = prefix match {
    case userPrefix(nick, user, host) =>
      Some(Prefix(nick, user, host.trim))
    case hostPrefix(host) =>
      Some(Prefix("", "", host))
    case _ => None
  }

  @tailrec
  private def parseParams(remaining: String, cur: List[String] = Nil): List[String] =
    if (remaining.isEmpty) cur.reverse
    else if (remaining.startsWith(":")) { //remaining - last param
      (remaining.drop(1) :: cur).reverse
    } else {
      val split        = remaining.split(" ", 2)
      val (param, rem) = (split(0), if (split.length > 1) split(1) else "")
      parseParams(rem, param :: cur)
    }

  def parser(): ZIO[Api with Console, Throwable, Unit] =
    for {
      raw        <- Api.dequeueReceived()
      _          <- putStrLn("Got to parse: " + raw)
      ircMessage <- parse(raw)
      _          <- Api.enqueueParsed(ircMessage)
    } yield ()

}
