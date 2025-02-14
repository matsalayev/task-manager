package tm.support.logback.requests

import io.circe.Json
import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.model.Method
import tm.support.sttp.SttpRequest

case class SendError(error: String)

object SendError {
  implicit val sttpRequest: SttpRequest[SendError, Json] =
    new SttpRequest[SendError, Json] {
      val method: Method = Method.GET
      val path: Path = _ => "/sendMessage"

      override def params: Params = req => Map("text" -> req.error)
      override def headers: Headers = _ =>
        Map(
          HeaderNames.ContentType -> MediaType.ApplicationXWwwFormUrlencoded.toString()
        )
      def body: Body = noBody
    }
}
