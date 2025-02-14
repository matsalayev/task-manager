package tm.support.syntax

import java.time.ZonedDateTime

import cats.MonadThrow
import cats.effect.Sync
import cats.effect.kernel.Concurrent
import cats.implicits._
import io.circe.Decoder
import io.circe.Encoder
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Accept-Language`
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.Part

import tm.Language
import tm.exception.AError
import tm.exception.AError.UnprocessableEntity
import tm.support.http4s.utils.MapConvert
import tm.support.http4s.utils.MapConvert.ValidationResult

trait Http4sSyntax {
  implicit def http4SyntaxReqOps[F[_]: JsonDecoder: MonadThrow](
      request: Request[F]
    ): RequestOps[F] =
    new RequestOps(request)
  implicit def http4SyntaxPartOps[F[_]](parts: Vector[Part[F]]): PartOps[F] =
    new PartOps(parts)

  implicit def deriveEntityEncoder[F[_], A: Encoder]: EntityEncoder[F, A] =
    jsonEncoderOf[F, A]

  implicit def deriveEntityDecoder[F[_]: Concurrent, A: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]

  implicit val zonedDateTimeQueryParamDecoder: QueryParamDecoder[ZonedDateTime] =
    QueryParamDecoder[String].map(ZonedDateTime.parse)
}

final class RequestOps[F[_]: MonadThrow](private val request: Request[F]) extends Http4sDsl[F] {
  implicit def lang: Language =
    request
      .headers
      .get[`Accept-Language`]
      .map(_.values.head.primaryTag)
      .flatMap(Language.withNameOption)
      .getOrElse(Language.En)

  def decodeR[A](
      handle: A => F[Response[F]]
    )(implicit
      decoder: Decoder[A],
      jsonDecoder: JsonDecoder[F],
    ): F[Response[F]] =
    request
      .asJson
      .map(json => decoder.decodeAccumulating(json.hcursor))
      .flatMap(
        _.fold(
          { error =>
            val errors = error.toList.map(_.getMessage).mkString("\n| ")
            UnprocessableEntity(AError.UnprocessableEntity(errors).json)
          },
          handle,
        )
      )
}

final class PartOps[F[_]](private val parts: Vector[Part[F]]) {
  private def filterFileTypes(part: Part[F]): Boolean = part.filename.exists(_.trim.nonEmpty)

  def fileParts: Vector[Part[F]] = parts.filter(filterFileTypes)

  def fileParts(mediaTypes: MediaType*): Vector[Part[F]] =
    parts.filter(_.headers.get[`Content-Type`].exists(h => mediaTypes.contains(h.mediaType)))

  def isFilePartExists: Boolean = parts.exists(filterFileTypes)

  def textParts: Vector[Part[F]] = parts.filterNot(filterFileTypes)

  def convert[A](implicit mapper: MapConvert[F, ValidationResult[A]], F: Sync[F]): F[A] =
    for {
      collectKV <- textParts.traverse { part =>
        part.bodyText.compile.foldMonoid.map(t => part.name.map(_ -> t))
      }
      entity <- mapper.fromMap(collectKV.flatten.toMap)
      validEntity <- entity.fold(
        error => F.raiseError[A](UnprocessableEntity(error.toList.mkString(" | "))),
        success => success.pure[F],
      )
    } yield validEntity
}
