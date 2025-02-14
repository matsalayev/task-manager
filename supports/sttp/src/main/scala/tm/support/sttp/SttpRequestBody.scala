package tm.support.sttp

import cats.Show
import io.circe.Json
import sttp.client3.RequestBody
import sttp.model.Part

sealed trait SttpRequestBody

object SttpRequestBody {
  case object SttpRequestNoBody extends SttpRequestBody
  case class SttpRequestStringBody(body: String) extends SttpRequestBody
  case class SttpRequestJsonBody(body: Json) extends SttpRequestBody
  case class SttpRequestFormBody(body: Map[String, String]) extends SttpRequestBody
  case class SttpRequestMultiPart(
      parts: Part[RequestBody[Any]]*
    ) extends SttpRequestBody

  implicit val show: Show[SttpRequestBody] = Show.show {
    case SttpRequestNoBody => "no-body"
    case SttpRequestStringBody(body) => body
    case SttpRequestJsonBody(body) => body.spaces2
    case req: SttpRequestMultiPart =>
      req.parts.map(_.body.show).reduceOption(_ + ", " + _).getOrElse("")
    case SttpRequestFormBody(body) =>
      body
        .map { case (k, v) => k + "=" + v }
        .reduceOption(_ + "&" + _)
        .getOrElse("")
  }
}
