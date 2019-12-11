package votbot.event.handlers.ultimatequotes

import votbot.event.handlers.CommandHandler
import votbot.event.handlers.ultimatequotes.UltimateQuotes.HandlerEnv
import votbot.model.irc.UserKey
import votbot.{ Api, Configuration, HttpClient }
import zio.ZIO

import scala.util.matching.Regex

trait SubCommand {
  val cmdRegex: Regex
  val description: String
  def action(subCmd: String, isAdmin: Boolean): ZIO[HandlerEnv, Throwable, Option[String]]
}

object UltimateQuotes extends CommandHandler {
  private val subCommands: List[SubCommand] = List(Add, Get, Count)
  override val commands: List[String]       = List("uq")
  override val description: String          = "ultimate quotes"

  override def response(
    sender: String,
    channel: String,
    command: String,
    args: String
  ): ZIO[HandlerEnv, Throwable, Unit] =
    for {
      api       <- ZIO.access[Api](_.api)
      cfg       <- ZIO.access[Configuration](_.config)
      userAcc   <- api.getUserAccountName(UserKey(sender))
      isAdmin   = cfg.admin.account.equalsIgnoreCase(userAcc)
      responses <- ZIO.foreach(subCommands)(sc => sc.action(args, isAdmin)).map(_.filter(_.nonEmpty))
      _         <- ZIO.foreach(responses)(r => api.sendChannelMessage(channel, r.get))
    } yield ()

}
