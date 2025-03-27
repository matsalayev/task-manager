package tm.endpoint.routes

import cats.effect.Concurrent
import cats.implicits._
import org.http4s._
import org.http4s.circe.JsonDecoder
import org.typelevel.log4cats.Logger

import tm.domain.auth.AuthedUser
import tm.effects.FileLoader
import tm.services.ProjectsService
import tm.support.http4s.utils.Routes
import tm.support.syntax.all.deriveEntityEncoder

final case class ProjectsRoutes[F[_]: Concurrent: FileLoader: JsonDecoder](
    projects: ProjectsService[F]
  )(implicit
    logger: Logger[F]
  ) extends Routes[F, AuthedUser] {
  override val path = "/projects"

  override val public: HttpRoutes[F] = HttpRoutes.empty

  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {
    case GET -> Root / "get" as user =>
      projects.get(user.corporateId).flatMap(Ok(_))
  }
}
