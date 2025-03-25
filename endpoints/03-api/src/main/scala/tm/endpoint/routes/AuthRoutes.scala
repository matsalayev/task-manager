package tm.endpoint.routes

import cats.Monad
import cats.MonadThrow
import cats.implicits._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.JsonDecoder
import org.typelevel.log4cats.Logger

import tm.Language
import tm.auth.impl.Auth
import tm.domain.auth.AuthedUser
import tm.domain.auth.Credentials
import tm.support.http4s.utils.Routes
import tm.support.syntax.http4s.http4SyntaxReqOps

final case class AuthRoutes[F[_]: Monad: JsonDecoder: MonadThrow](
    auth: Auth[F, AuthedUser]
  )(implicit
    logger: Logger[F]
  ) extends Routes[F, AuthedUser] {
  override val path = "/auth"

  override val public: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST -> Root / "login" =>
        req.decodeR[Credentials] { credentials =>
          auth.login(credentials).flatMap(Ok(_))
        }

      case req @ GET -> Root / "refresh" =>
        auth.refresh(req).flatMap(Ok(_))
    }

  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {
    case ar @ GET -> Root / "logout" as user =>
      auth.destroySession(ar.req, user.phone) *> NoContent()
  }
}
