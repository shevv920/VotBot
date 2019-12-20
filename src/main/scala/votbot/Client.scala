package votbot

import java.nio.charset.StandardCharsets

import votbot.Main.VotbotEnv
import votbot.event.Event.Connected
import zio.console.putStrLn
import zio.duration._
import zio.nio.channels.AsynchronousSocketChannel
import zio.nio.{ InetSocketAddress, SocketAddress }
import zio.{ Chunk, Schedule, Task, ZIO }

object Client {
  val maxMessageLength = 512

  def make(): ZIO[VotbotEnv, Exception, Unit] =
    for {
      config <- ZIO.access[Configuration](_.configuration.config)
      _ <- AsynchronousSocketChannel()
            .use { channel =>
              for {
                addr <- SocketAddress.inetSocketAddress(config.server.address, config.server.port)
                _    <- connection(channel, addr)
              } yield ()
            }
            .onError(e => putStrLn(e.untraced.prettyPrint))
            .retry(Schedule.spaced(5.seconds))
    } yield ()

  def connection(channel: AsynchronousSocketChannel, addr: InetSocketAddress): ZIO[VotbotEnv, Exception, Unit] =
    for {
      _        <- putStrLn("connecting to: " + addr.toString())
      _        <- channel.connect(addr)
      mbRemote <- channel.remoteAddress
      remote   <- ZIO.fromOption(mbRemote).mapError(_ => new Exception("Not connected"))
      _        <- ZIO.accessM[Api](_.api.enqueueEvent(Connected(remote)))
      reader   <- reader(channel).fork
      writer   <- writer(channel).fork
      _        <- reader.zip(writer).await
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
      _            <- ZIO.accessM[Api](_.api.enqueueReceived(valid: _*))
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
}