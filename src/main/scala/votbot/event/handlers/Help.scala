package votbot.event.handlers
import votbot.Api
import zio.ZIO

object Help extends CommandHandler {
  override val commands: List[String] = List("help", "h")
  override val description: String    = "Все команды которые умеет бот: "
  override def response(channel: String, cmd: String, arg: String): ZIO[HandlerEnv, Throwable, Unit] =
    for {
      api              <- ZIO.access[Api](_.api)
      baseEventHandler <- ZIO.environment[BaseEventHandler]
      customHandlers   <- baseEventHandler.customHandlers.get
      commandHandlers <- ZIO.effect(
                          customHandlers
                            .filter {
                              case _: CommandHandler => true
                              case _                 => false
                            }
                            .map(_.asInstanceOf[CommandHandler])
                        )
      _ <- ZIO.when(arg.isEmpty) {
            ZIO
              .effect(commandHandlers.map(_.commands.head))
              .flatMap(allCommands => api.sendChannelMessage(channel, description + allCommands.mkString(", ")))
          }
      _ <- ZIO.when(arg.nonEmpty) {
            for {
              chs <- ZIO.effect(commandHandlers.filter(_.commands.contains(arg)))
              _ <- ZIO.when(chs.nonEmpty) {
                    ZIO
                      .effect(chs.map(_.helpMessage).mkString(", "))
                      .flatMap(str => api.sendChannelMessage(channel, str))
                  }
            } yield ()
          }
    } yield ()
}
