package votbot.event.handlers
import votbot.Api
import votbot.Main.VotbotEnv
import votbot.event.BaseEventHandler
import zio.ZIO

object Help extends CommandHandler {
  override  val commands: List[String] = List("help", "h", "\\?", "что умеешь\\?")
  override  val description: String = "Все команды которые умеет бот"
  override def response(channel:  String): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      api <- ZIO.environment[Api]
      baseEventHandler <- ZIO.environment[BaseEventHandler]
      customHandlers <- baseEventHandler.customHandlers.get
      commandHandlers = customHandlers.filter {
        case _: CommandHandler => true
        case _ => false
      }.map(_.asInstanceOf[CommandHandler])
      commands = commandHandlers.map(_.commands.head)
      _ <- api.sendChannelMessage(channel, "Команды: " + commands.mkString(", "))
    } yield ()
}
