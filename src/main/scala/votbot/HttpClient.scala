package votbot

import sttp.client.{ SttpBackend, _ }
import sttp.model.Uri
import zio.{ Task, ZIO }
import zio.blocking._

trait HttpClient {
  val httpClient: HttpClient.Service[Any]
}

object HttpClient {

  trait Service[R] {
    def quick(uri: Uri): ZIO[Blocking, Throwable, Response[String]]
  }
}

object DefaultHttpClient extends HttpClient.Service[Any] {
  implicit val sttpBackend = HttpURLConnectionBackend()

  override def quick(uri: Uri): ZIO[Blocking, Throwable, Identity[Response[String]]] =
    effectBlocking {
      val r = quickRequest.get(uri)
      r.send()
    }
}
