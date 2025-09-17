package tm.endpoint.routes

import cats.effect.implicits.genSpawnOps
import cats.effect.kernel.Concurrent
import cats.implicits.catsSyntaxApplicativeError
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import eu.timepit.refined.types.string.NonEmptyString
import org.http4s.AuthedRoutes
import org.http4s.HttpRoutes
import org.http4s.circe.JsonDecoder
import org.http4s.circe._
import org.typelevel.log4cats.Logger

import tm.domain.auth.AuthedUser
import tm.domain.enums.BotType
import tm.domain.telegram.Update
import tm.services.CorporateBotService
import tm.services.EmployeeBotService
import tm.support.http4s.utils.Routes
import tm.syntax.all.circeSyntaxJsonDecoderOps

final case class TelegramBotsRoutes[F[_]: JsonDecoder: Concurrent](
    corporateBotService: CorporateBotService[F],
    employeeBotService: EmployeeBotService[F],
    webhookSecret: NonEmptyString,
  )(implicit
    logger: Logger[F]
  ) extends Routes[F, AuthedUser] {
  override val path = "/telegram"

  override val public: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST -> Root / "bot" / "webhook" / botType =>
        val process =
          req
            .headers
            .headers
            .find(h => h.name.toString == "X-Telegram-Bot-Api-Secret-Token") match {
            case Some(header) =>
              if (header.value == webhookSecret.toString())
                req
                  .asJson
                  .flatMap(a => logger.info(a.spaces2) *> a.decodeAsF[F, Update])
                  .flatMap { update =>
                    BotType.withName(botType) match {
                      case BotType.Corporate => corporateBotService.telegramMessage(update)
                      case BotType.Employee => employeeBotService.telegramMessage(update)
                      case _ => logger.info(s"BotType not found: $botType")
                    }
                  }
                  .handleErrorWith { e =>
                    logger.error(e)("Error while processing telegram webhook update")
                  }
              else
                logger.error("mismatching webhook secret key")
            case _ => logger.error("no webhook secret key")
          }
        process.start.flatMap(_.join).void.flatMap(_ => Ok("ok")).handleErrorWith(_ => Ok("OK"))
    }

  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.empty
}
