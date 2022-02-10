package votbot

import sttp.client.{ SttpBackend, _ }
import sttp.model.Uri
import votbot.Configuration.Configuration
import zio.ZIO

import zio.Clock

import zio._
import zio.ZIO.attemptBlocking

object HttpClient {
  type HttpClient = HttpClient.Service

  trait Service {
    def quick(uri: Uri): ZIO[Any with Clock, Throwable, Response[String]]
  }

  case class DefaultHttpClient(cfg: Configuration) extends Service {
    implicit val sttpBackend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

    override def quick(uri: Uri): ZIO[Any with Clock, Throwable, Identity[Response[String]]] =
      attemptBlocking {
        val r = quickRequest.get(uri)
        r.send()
      }.timeoutFail(new Exception("timeout " + cfg.http.quickRequestTimeout + " seconds"))(
        cfg.http.quickRequestTimeout.seconds
      )
  }

  val defaultHttpClient: URLayer[Configuration, HttpClient] = (DefaultHttpClient(_)).toLayer[HttpClient]
}
