package votbot

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import pureconfig.generic.auto._
import votbot.event.Event._
import votbot.event.handlers.{BaseEventHandler, Help, Quotes}
import votbot.event.{Event, EventHandler}
import votbot.model.Bot.State
import votbot.model.Irc.{Channel, RawMessage, User}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{Console, _}
import zio.nio.SocketAddress
import zio.nio.channels.AsynchronousSocketChannel
import zio.random.Random

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

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    mainLogic
      .provideSomeM(
        for {
          cfg      <- ZIO.fromEither(pureconfig.loadConfig[Config](Paths.get("../application.conf")))
          st       <- Ref.make(State(cfg.bot.nick))
          inQ      <- Queue.unbounded[String]
          outQ     <- Queue.unbounded[RawMessage]
          pQ       <- Queue.unbounded[RawMessage]
          evtQ     <- Queue.unbounded[Event]
          chs      <- Ref.make(Map.empty[String, Channel])
          handlers <- Ref.make(List[EventHandler](Quotes, Help))
          users    <- Ref.make(Map.empty[String, User])
        } yield new VotbotEnv with BasicEnv with LiveApi with BaseEventHandler with Blocking.Live {
          override val knownUsers: Ref[Map[String, User]]      = users
          override val customHandlers: Ref[List[EventHandler]] = handlers
          override val config: Config                          = cfg
          override val state: Ref[State]                       = st
          override val parseQ: Queue[String]                   = inQ
          override val processQ: Queue[RawMessage]             = pQ
          override val outMessageQ: Queue[RawMessage]          = outQ
          override val eventQ: Queue[Event]                    = evtQ
          override val knownChannels: Ref[Map[String, Channel]]     = chs
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
              _            <- ZIO.accessM[Api](_.enqueueEvent(Connected))
              reader       <- reader(channel).fork
              writer       <- writer(channel).fork
              parser       <- MsgParser.parser().forever.fork
              processor    <- processor().forever.fork
              evtProcessor <- Event.eventProcessor().forever.fork
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
      _            <- ZIO.accessM[Api](_.enqueueParse(valid: _*))
      _            <- reader(channel, rem.mkString(""))
    } yield ()

  private def split(str: String): Task[(Array[String], Array[String])] =
    ZIO.effect(str.split("(?<=\r\n)").filter(_.nonEmpty).span(_.endsWith("\r\n")))

  def writer(channel: AsynchronousSocketChannel, rem: Chunk[Byte] = Chunk.empty): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      msg      <- ZIO.accessM[Api](_.dequeueOutMessage())
      msgBytes <- MsgParser.msgToByteArray(msg)
      chunk    = Chunk.fromArray(msgBytes)
      remN     <- channel.write(rem ++ chunk)
      rem      = chunk.drop(remN)
      _        <- putStrLn("written: " + msg + " remaining: " + rem.length)
      _        <- writer(channel, rem)
    } yield ()

  def processor(): ZIO[VotbotEnv, Throwable, Unit] =
    for {
      api <- ZIO.environment[Api]
      msg <- api.dequeueProcess()
      _   <- putStrLn("Processing IRC Message: " + msg.toString)
      evt <- Event.ircToEvent(msg)
      _   <- api.enqueueEvent(evt)
    } yield ()

}
