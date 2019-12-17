package votbot.event.handlers

import votbot.Main.VotbotEnv
import votbot.{ Api, Configuration }
import votbot.model.irc.UserKey
import zio.ZIO

object Admin extends CommandHandler {
  override val commands: List[String]         = List("!", "admin")
  override val description: String            = "_"
  private val subCommands: List[AdminCommand] = List.empty

  override def response(
    sender: String,
    channel: String,
    command: String,
    args: String
  ): ZIO[HandlerEnv, Throwable, Unit] =
    for {
      api     <- ZIO.access[Api](_.api)
      cfg     <- ZIO.access[Configuration](_.config)
      user    <- api.getUser(UserKey(sender))
      userAcc <- api.getUserAccountName(UserKey(sender))
      isAdmin = cfg.admin.account.equalsIgnoreCase(userAcc)
      _ <- ZIO.when(isAdmin) {
            ZIO.foreach(subCommands)(_.action(command, args))
          }
    } yield ()
}

trait AdminCommand {
  def action(command: String, args: String): ZIO[VotbotEnv, Throwable, Unit]

}
