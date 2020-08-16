package votbot

import java.nio.charset.StandardCharsets

import votbot.Api.Api
import votbot.Configuration.Configuration
import votbot.event.Event.Connected
import votbot.model.irc.Message
import zio.console.{ Console, putStrLn }
import zio.duration._
import zio.nio.channels.AsynchronousSocketChannel
import zio.nio.core.{ InetSocketAddress, SocketAddress }
import zio.{ Chunk, Schedule, Task, ZIO }

object Client {
  val maxMessageLength = 512

  def make() =
    for {
      config <- Configuration.config
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

  def connection(channel: AsynchronousSocketChannel, addr: InetSocketAddress): ZIO[Api with Console, Exception, Unit] =
    for {
      _        <- putStrLn("connecting to: " + addr.toString())
      _        <- channel.connect(addr)
      mbRemote <- channel.remoteAddress
      remote   <- ZIO.fromOption(mbRemote).mapError(_ => new Exception("Not connected"))
      _        <- ZIO.accessM[Api](_.get.enqueueEvent(Connected(remote)))
      reader   <- reader(channel).fork
      writer   <- writer(channel).fork
      _        <- reader.zip(writer).await
    } yield ()

  def reader(
    channel: AsynchronousSocketChannel,
    rem: String = ""
  ): ZIO[Api with Console, Throwable, Unit] =
    for {
      chunk        <- channel.read(maxMessageLength)
      str          <- ZIO.effect(rem + new String(chunk.toArray, StandardCharsets.UTF_8))
      res          <- split(str)
      (valid, rem) = res
      _ <- ZIO.foreach(valid.toList) { v =>
            ZIO.accessM[Api](_.get.enqueueReceived((v)))
          }
      _ <- reader(channel, rem.mkString(""))
    } yield ()

  private def split(str: String): Task[(Array[String], Array[String])] =
    ZIO.effect(str.split("(?<=\r\n)").filter(_.nonEmpty).span(_.endsWith("\r\n")))

  def writer(
    channel: AsynchronousSocketChannel,
    rem: Chunk[Byte] = Chunk.empty
  ): ZIO[Api with Console, Throwable, Unit] =
    for {
      msg      <- ZIO.accessM[Api](_.get.dequeueOutMessage())
      msgBytes <- Message.toByteArray(msg)
      chunk    = rem ++ Chunk.fromArray(msgBytes)
      remN     <- channel.write(chunk)
      rem      = chunk.drop(remN)
      _        <- putStrLn("Written: " + new String(msgBytes, StandardCharsets.UTF_8) + " remaining: " + rem.length)
      _        <- writer(channel, rem)
    } yield ()
}
