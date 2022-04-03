package votbot

import java.nio.charset.StandardCharsets

import votbot.Api.Api
import votbot.Configuration.Configuration
import votbot.event.Event.Connected
import votbot.model.irc.Message
import zio.Clock

import zio.nio.channels.AsynchronousSocketChannel
import zio.nio.InetSocketAddress
import zio.{ Chunk, Schedule, Task, ZIO }
import zio.{ Console, _ }
import zio.Console.printLine

object Client {
  val maxMessageLength = 512

  def make(): ZIO[Api with Console with Configuration with Clock, Exception, Unit] =
    for {
      config <- Configuration.config
      _ <- ZIO.scoped {
            AsynchronousSocketChannel.open
              .flatMap { channel =>
                for {
                  addr <- InetSocketAddress.hostNameResolved(config.server.address, config.server.port)
                  _    <- connection(channel, addr)
                } yield ()
              }
              .onError(e => printLine(e.untraced.prettyPrint).orDie)
              .retry(Schedule.spaced(5.seconds))
          }
    } yield ()

  def connection(
    channel: AsynchronousSocketChannel,
    addr: InetSocketAddress
  ): ZIO[Api with Console with Clock, Exception, Unit] =
    for {
      _        <- printLine("connecting to: " + addr.toString())
      _        <- channel.connect(addr)
      mbRemote <- channel.remoteAddress
      remote   <- ZIO.fromOption(mbRemote).orElseFail(new Exception("Not connected"))
      _        <- ZIO.environmentWithZIO[Api](_.get.enqueueEvent(Connected(remote)))
      reader   <- reader(channel).fork
      writer   <- writer(channel).fork
      _        <- reader.zip(writer).await
    } yield ()

  def reader(
    channel: AsynchronousSocketChannel,
    rem: String = ""
  ): ZIO[Api with Console, Throwable, Unit] =
    for {
      chunk        <- channel.readChunk(maxMessageLength)
      str          <- ZIO.attempt(rem + new String(chunk.toArray, StandardCharsets.UTF_8))
      res          <- split(str)
      (valid, rem) = res
      _            <- ZIO.foreachDiscard(valid.toList)(v => ZIO.environmentWithZIO[Api](_.get.enqueueReceived(v)))
      _            <- reader(channel, rem.mkString(""))
    } yield ()

  private def split(str: String): Task[(Array[String], Array[String])] =
    ZIO.attempt(str.split("(?<=\r\n)").filter(_.nonEmpty).span(_.endsWith("\r\n")))

  def writer(
    channel: => AsynchronousSocketChannel
  ): ZIO[Api with Clock, Throwable, Unit] =
    (for {
      msg      <- ZIO.serviceWithZIO[Api](_.dequeueOutMessage())
      msgBytes <- Message.toByteArray(msg)
      chunk    = Chunk.fromArray(msgBytes)
      _        <- channel.writeChunk(chunk)
    } yield ()).forever
}
