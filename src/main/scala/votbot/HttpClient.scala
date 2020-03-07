package votbot

import sttp.client.{ SttpBackend, _ }
import sttp.model.Uri
import votbot.Configuration.Configuration
import zio.{ Has, ZIO, ZLayer }
import zio.blocking._
import zio.clock.Clock
import zio.duration._

object HttpClient {
  type HttpClient = Has[HttpClient.Service]

  trait Service {
    def quick(uri: Uri): ZIO[Blocking with Clock, Throwable, Response[String]]
  }

  val defaultHttpClient: ZLayer[Configuration, Nothing, HttpClient] =
    ZLayer.fromFunction((cfg: Configuration) =>
      new Service {
        implicit val sttpBackend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

        override def quick(uri: Uri): ZIO[Blocking with Clock, Throwable, Identity[Response[String]]] =
          effectBlocking {
            val r = quickRequest.get(uri)
            r.send()
          }.timeoutFail(new Exception("timeout " + cfg.get.http.quickRequestTimeout + " seconds"))(
            cfg.get.http.quickRequestTimeout.seconds
          )
      }
    )
}
