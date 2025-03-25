package tm.endpoint.routes

import cats.effect.Concurrent
import cats.implicits._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.JsonDecoder
import org.typelevel.log4cats.Logger
import tm.domain.auth.AuthedUser
import tm.effects.FileLoader
import tm.support.http4s.utils.Routes

final case class FormRoutes[F[_]: FileLoader: JsonDecoder: Concurrent](
  )(implicit
    logger: Logger[F]
  ) extends Routes[F, AuthedUser] {
  override val path = "/form"

  override val public: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "create-employee" =>
        FileLoader[F].resourceAsString("form-template.html").flatMap(content=>
      Ok(content.trim).map(_.withContentType(headers.`Content-Type`(MediaType.text.html))))

      case req @ POST -> Root / "submit" =>
        req.as[UrlForm].flatMap { form =>
          val name = form.values.get("name").flatMap(_.headOption).getOrElse("Unknown")
            Ok("""<script>window.close();</script>""").map(_.withContentType(headers.`Content-Type`(MediaType.text.html)))
        }
    }

  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {
    case ar @ GET -> Root / "logout" as user => Ok()
  }
}
