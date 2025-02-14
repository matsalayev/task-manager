package tm.support.http4s

import scala.util.control.NonFatal

import cats.MonadThrow
import cats.implicits.catsSyntaxFlatMapOps
import org.http4s.HttpRoutes
import org.http4s.MalformedMessageBodyFailure
import org.http4s.Response
import org.http4s.dsl.Http4sDsl
import org.typelevel.log4cats.Logger
import tm.Mode
import tm.Mode.Production
import tm.exception.AError
import tm.exception.AError.AuthError
import tm.support.http4s.utils.HttpErrorHandler
import tm.support.syntax.all.deriveEntityEncoder

class LiveHttpErrorHandler[F[_]: MonadThrow](
    implicit
    logger: Logger[F]
  ) extends HttpErrorHandler[F, AError]
       with Http4sDsl[F] {
  private val handler: PartialFunction[Throwable, F[Response[F]]] = {
    case err: AuthError.NoSuchUser =>
      logger.info(err.getMessage) >>
        Forbidden(err.json)
    case err: AuthError =>
      logger.info(err.getMessage) >>
        Forbidden(err.json)
    case error: AError.UnprocessableEntity =>
      logger.info(error)("Invalid form data entered") >>
        UnprocessableEntity(error.json)
    case error: AError =>
      logger.info(error)("Error occurred") >>
        BadRequest(error.json)
    case error: MalformedMessageBodyFailure =>
      logger.info(error)("Invalid json entered") >>
        UnprocessableEntity(AError.UnprocessableEntity(error.details).json)
    case error: IllegalArgumentException =>
      val message = error.getMessage.replace("requirement failed: ", "")
      logger.info(error)("Incorrect argument entered") >>
        UnprocessableEntity(AError.UnprocessableEntity(message).json)
    case NonFatal(throwable) =>
      logger.error(throwable)("Error occurred while processing request") >> {
        if (Mode.current == Production)
          InternalServerError(AError.Internal("Internal server error").json)
        else InternalServerError(AError.Internal(throwable.getMessage).json)
      }
  }

  override def handle(routes: HttpRoutes[F]): HttpRoutes[F] =
    RoutesHttpErrorHandler(routes)(handler)
}
