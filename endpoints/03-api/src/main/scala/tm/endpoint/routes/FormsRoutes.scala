package tm.endpoint.routes

import cats.effect.Concurrent
import cats.implicits._
import org.http4s._
import org.http4s.circe.JsonDecoder
import org.typelevel.log4cats.Logger

import tm.domain.auth.AuthedUser
import tm.domain.corporate.CreateEmployee
import tm.effects.FileLoader
import tm.services.EmployeeService
import tm.support.http4s.utils.Routes
import tm.support.syntax.all.http4SyntaxReqOps

final case class FormsRoutes[F[_]: Concurrent: FileLoader: JsonDecoder](
    employeeService: EmployeeService[F]
  )(implicit
    logger: Logger[F]
  ) extends Routes[F, AuthedUser] {
  override val path = "/forms"

  override val public: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "login" =>
      FileLoader[F]
        .resourceAsString("forms/login.html")
        .flatMap(content =>
          Ok(content.trim).map(_.withContentType(headers.`Content-Type`(MediaType.text.html)))
        )

    case GET -> Root / "create-employee" / _ =>
      FileLoader[F]
        .resourceAsString("forms/create-employee.html")
        .flatMap(content =>
          Ok(content.trim).map(_.withContentType(headers.`Content-Type`(MediaType.text.html)))
        )
  }

  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {

    case ar @ POST -> Root / "create-employee" / "submit" as user =>
      ar.req
        .decodeR[CreateEmployee] { data =>
          employeeService.create(data, user.id).flatMap(Created(_))
        }
        .handleErrorWith { error =>
          BadRequest(error.getMessage)
        }
  }
}
