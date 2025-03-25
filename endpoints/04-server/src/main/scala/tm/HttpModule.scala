package tm

import cats.data.NonEmptyList
import cats.effect.Async
import cats.effect.ExitCode
import cats.effect.kernel.Resource
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.circe.JsonDecoder
import org.http4s.server.Router
import org.typelevel.log4cats.Logger

import tm.domain.auth.AuthedUser
import tm.endpoint.routes._
import tm.http.Environment
import tm.support.http4s.HttpServer
import tm.support.http4s.utils.Routes

object HttpModule {
  private def allRoutes[F[_]: Async: JsonDecoder: Logger](
      env: Environment[F]
    ): NonEmptyList[HttpRoutes[F]] =
    NonEmptyList
      .of[Routes[F, AuthedUser]](
        new TelegramBotsRoutes[F](
          env.services.corporateBotService,
          env.services.employeeBotService,
          env.telegramCorporateBot.webhookSecret,
        ),
        new FormRoutes[F](),
        new AuthRoutes[F](env.services.auth),
        new AssetsRoutes[F](env.services.assets),
      )
      .map { r =>
        Router(
          r.path -> (r.public <+> env.middlewares.user(r.`private`))
        )
      }

  def make[F[_]: Async](
      env: Environment[F]
    )(implicit
      logger: Logger[F]
    ): Resource[F, F[ExitCode]] =
    HttpServer.make[F](env.config, implicit wbs => allRoutes[F](env)).map { _ =>
      logger.info(s"TM http server is started").as(ExitCode.Success)
    }
}
