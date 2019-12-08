package votbot

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import votbot.event.Event._
import votbot.event.handlers.{ BaseEventHandler, Help, Quotes }
import votbot.event.{ Event, EventHandler }
import votbot.model.Bot.State
import votbot.model.irc._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{ Console, _ }
import zio.nio.SocketAddress
import zio.nio.channels.AsynchronousSocketChannel
import zio.random.Random
import pureconfig.generic.auto._
import votbot.event.handlers.ultimatequotes.UltimateQuotes

object Main extends App {
  val maxMessageLength = 512
  trait BasicEnv extends Console.Live with Clock.Live with Random.Live

  trait VotbotEnv
      extends Console
      with Clock
      with Random
      with Configuration
      with BotState
      with Api
      with BaseEventHandler
      with Blocking
      with HttpClient
      with Database

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    mainLogic
      .provideSomeM(
        for {
          cfg      <- ZIO.fromEither(pureconfig.loadConfig[Config](Paths.get("application.conf"))) //fixme - paths
          st       <- Ref.make(State(cfg.bot.nick))
          inQ      <- Queue.unbounded[String]
          outQ     <- Queue.unbounded[RawMessage]
          pQ       <- Queue.unbounded[RawMessage]
          evtQ     <- Queue.unbounded[Event]
          chs      <- Ref.make(Map.empty[ChannelKey, Channel])
          handlers <- Ref.make(List[EventHandler](Quotes, Help, UltimateQuotes))
          users    <- Ref.make(Map.empty[UserKey, User])
        } yield new VotbotEnv
          with BasicEnv
          with Api
          with BaseEventHandler
          with Blocking.Live
          with HttpClient
          with TestDatabase {
          override val customHandlers: Ref[List[EventHandler]] = handlers
          override val config: Config                          = cfg

          override val state: BotState.Service[Any] = new BotStateLive[Any] {
            override protected val state: Ref[State] = st
          }

          override val api: Api.Service[Any] = new DefaultApi[Any] {
            override protected val parseQ: Queue[String]                        = inQ
            override protected val processQ: Queue[RawMessage]                  = pQ
            override protected val outMessageQ: Queue[RawMessage]               = outQ
            override protected val eventQ: Queue[Event]                         = evtQ
            override protected val knownChannels: Ref[Map[ChannelKey, Channel]] = chs
            override protected val knownUsers: Ref[Map[UserKey, User]]          = users
          }

          override val httpClient: HttpClient.Service[Any] = DefaultHttpClient
        }
      )
      .either
      .map(_.fold(e => { println(e); 1 }, _ => 0))

  def mainLogic: ZIO[VotbotEnv, Any, Unit] =
    for {
      client <- client.fork
      _      <- client.await
    } yield ()

  def client: ZIO[VotbotEnv, Exception, Unit] =
    for {
      config <- ZIO.access[Configuration](_.config)
      addr   <- SocketAddress.inetSocketAddress(config.server.address, config.server.port)
      _ <- AsynchronousSocketChannel().use { channel =>
            for {
              _            <- channel.connect(addr)
              _            <- ZIO.accessM[Api](_.api.enqueueEvent(Connected))
              reader       <- reader(channel).fork
              writer       <- writer(channel).fork
              parser       <- MsgParser.parser().forever.fork //todo daemon?
              processor    <- processor().forever.fork //todo daemon?
              evtProcessor <- Event.eventProcessor().forever.fork //todo daemon?
              _            <- reader.zip(writer).await
              _            <- parser.zip(processor).await
              _            <- evtProcessor.await
            } yield ()
          }
    } yield ()

  def reader(
    channel: AsynchronousSocketChannel,
    rem: String = ""
  ): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      chunk        <- channel.read(maxMessageLength)
      str          <- ZIO.effect(rem + new String(chunk.toArray, StandardCharsets.UTF_8))
      res          <- split(str)
      (valid, rem) = res
      _            <- ZIO.accessM[Api](_.api.enqueueParse(valid: _*))
      _            <- reader(channel, rem.mkString(""))
    } yield ()

  private def split(str: String): Task[(Array[String], Array[String])] =
    ZIO.effect(str.split("(?<=\r\n)").filter(_.nonEmpty).span(_.endsWith("\r\n")))

  def writer(channel: AsynchronousSocketChannel, rem: Chunk[Byte] = Chunk.empty): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      msg      <- ZIO.accessM[Api](_.api.dequeueOutMessage())
      msgBytes <- MsgParser.msgToByteArray(msg)
      chunk    = Chunk.fromArray(msgBytes)
      remN     <- channel.write(rem ++ chunk)
      rem      = chunk.drop(remN)
      _        <- putStrLn("Written: " + new String(msgBytes, StandardCharsets.UTF_8) + " remaining: " + rem.length)
      _        <- writer(channel, rem)
    } yield ()

  def processor(): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      api <- ZIO.access[Api](_.api)
      msg <- api.dequeueProcess()
      _   <- putStrLn("Processing IRC Message: " + msg.toString)
      evt <- Event.ircToEvent(msg)
      _   <- api.enqueueEvent(evt)
    } yield ()

}
