package votbot

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import votbot.event.Event._
import votbot.event.handlers.{ BaseEventHandler, Help }
import votbot.event.{ Event, EventHandler }
import votbot.model.Bot.State
import votbot.model.irc._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{ Console, _ }
import zio.nio.{ InetSocketAddress, SocketAddress }
import zio.nio.channels.AsynchronousSocketChannel
import zio.random.Random
import zio.system
import pureconfig._
import pureconfig.generic.auto._
import zio.duration._
import votbot.database.{
  ChannelSettingsRepo,
  DatabaseProvider,
  QuotesRepo,
  TestChannelSettingsRepo,
  TestDatabaseProvider,
  TestQuotesRepo
}
import votbot.event.handlers.ultimatequotes.UltimateQuotes

object Main extends App {
  val maxMessageLength = 512
  trait BaseEnv extends Console.Live with Clock.Live with Random.Live with Blocking.Live

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
      with DatabaseProvider
      with QuotesRepo
      with ChannelSettingsRepo

  private def mkCfgPath() =
    system
      .property("user.dir")
      .map(_.getOrElse(".") + "/" + "application.conf")
      .map(Paths.get(_))

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    mainLogic(args)
      .provideSomeM(
        for {
          cfgPath <- mkCfgPath()
          cfg <- ZIO
                  .fromEither(ConfigSource.file(cfgPath).load[Config])
                  .orElse(ZIO.fromEither(ConfigSource.default.load[Config]))
          st       <- Ref.make(State(cfg.bot.nick))
          inQ      <- Queue.unbounded[String]
          outQ     <- Queue.unbounded[RawMessage]
          pQ       <- Queue.unbounded[RawMessage]
          evtQ     <- Queue.unbounded[Event]
          chs      <- Ref.make(Map.empty[ChannelKey, Channel])
          handlers <- Ref.make(List[EventHandler](Help, UltimateQuotes))
          users    <- Ref.make(Map.empty[UserKey, User])
        } yield new VotbotEnv with BaseEnv with TestDatabaseProvider with QuotesRepo {
          override val channelSettingsRepo: ChannelSettingsRepo.Service[Any] = TestChannelSettingsRepo
          override val quotesRepo: QuotesRepo.Service[Any]                   = TestQuotesRepo
          override val customHandlers: Ref[List[EventHandler]]               = handlers
          override val config: Config                                        = cfg

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

  def mainLogic(args: List[String]): ZIO[VotbotEnv, Any, Unit] =
    for {
      qsRepo <- ZIO.access[QuotesRepo](_.quotesRepo)
      csRepo <- ZIO.access[ChannelSettingsRepo](_.channelSettingsRepo)
      _ <- ZIO.when(args.contains("--initdb")) {
            qsRepo.createSchemaIfNotExists *> csRepo.createSchemaIfNotExists
          }
      client <- client().fork
      _      <- client.await
    } yield ()

  def client(): ZIO[VotbotEnv, Exception, Unit] =
    for {
      config <- ZIO.access[Configuration](_.config)
      _ <- AsynchronousSocketChannel()
            .use { channel =>
              for {
                addr <- SocketAddress.inetSocketAddress(config.server.address, config.server.port)
                _    <- putStrLn("connecting to: " + addr.toString())
                _    <- connection(channel, addr)
              } yield ()
            }
            .onError(e => putStrLn(e.untraced.prettyPrint))
            .retry(Schedule.spaced(5.seconds))
            .fork
      parser       <- MsgParser.parser().forever.fork
      processor    <- processor().forever.fork
      evtProcessor <- Event.eventProcessor().forever.fork
      _            <- parser.zip(processor).await
      _            <- evtProcessor.await
    } yield ()

  def connection(channel: AsynchronousSocketChannel, addr: InetSocketAddress): ZIO[VotbotEnv, Exception, Unit] =
    for {
      _      <- channel.connect(addr)
      _      <- ZIO.accessM[Api](_.api.enqueueEvent(Connected))
      reader <- reader(channel).fork
      writer <- writer(channel).fork
      _      <- reader.zip(writer).await
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
