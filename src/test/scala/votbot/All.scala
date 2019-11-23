package votbot

import java.nio.charset.StandardCharsets

import votbot.event.Event
import votbot.event.Event.Event
import votbot.model.Irc
import votbot.model.Irc.{Channel, Command, RawMessage, User}
import zio.test.Assertion._
import zio.test._
import zio.{Managed, Queue, Ref, ZIO}
import zio.console._

object Base {

  val env = for {
    inQ   <- Queue.unbounded[String]
    outQ  <- Queue.unbounded[RawMessage]
    pQ    <- Queue.unbounded[RawMessage]
    evtQ  <- Queue.unbounded[Event]
    chs   <- Ref.make(Map.empty[String, Channel])
    users <- Ref.make(Set.empty[User])
  } yield new LiveApi with Console.Live {
    override protected val parseQ: Queue[String]                   = inQ
    override protected val processQ: Queue[RawMessage]             = pQ
    override protected val outMessageQ: Queue[RawMessage]          = outQ
    override protected val eventQ: Queue[Event.Event]              = evtQ
    override protected val channels: Ref[Map[String, Irc.Channel]] = chs
    override protected val knownUsers: Ref[Set[Irc.User]]          = users
  }
  val envM: Managed[Nothing, Api with Console] = env.toManaged_

}

object All
    extends DefaultRunnableSpec(
      suite("all")(
        testM("test1") {
          val msg = RawMessage(Command.Privmsg, "votbot", "message")
          for {
            api      <- ZIO.environment[Api]
            ba       <- MsgParser.msgToByteArray(msg)
            _        <- api.enqueueParse(new String(ba, StandardCharsets.UTF_8))
            _        <- MsgParser.parser()
            parseRes <- api.dequeueProcess()
          } yield assert(parseRes, equalTo(msg))
        }.provideManaged(Base.envM)
      )
    )
