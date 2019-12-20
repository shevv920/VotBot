package votbot

import sttp.client.{ SttpBackend, _ }
import sttp.model.Uri
import zio.{ Task, ZIO }
import zio.duration._
import zio.blocking._
import zio.clock.Clock

trait HttpClient {
  val httpClient: HttpClient.Service[Any]
}

object HttpClient {

  trait Service[R] {
    def quick(uri: Uri): ZIO[Blocking with Clock, Throwable, Response[String]]
  }
}

trait DefaultHttpClient extends HttpClient {
  val configuration: Configuration.Service[Any]

  override val httpClient: HttpClient.Service[Any] = new HttpClient.Service[Any] {
    implicit val sttpBackend = HttpURLConnectionBackend()

    override def quick(uri: Uri): ZIO[Blocking with Clock, Throwable, Identity[Response[String]]] =
      effectBlocking {
        val r = quickRequest.get(uri)
        r.send()
      }.timeoutFail(new Exception("timeout"))(configuration.config.http.quickRequestTimeout.seconds)
  }
}
