package tm.endpoint.routes

import cats.effect.Async
import cats.implicits._
import org.http4s._
import org.http4s.circe.JsonDecoder
import org.http4s.multipart.Multipart
import tm.ObjectId
import tm.domain.asset.FileMeta
import tm.domain.auth.AuthedUser
import tm.exception.AError
import tm.services.AssetsService
import tm.support.http4s.utils.Routes
import tm.support.syntax.all.deriveEntityEncoder
import tm.support.syntax.all.http4SyntaxPartOps
import tm.syntax.circe._

final case class AssetsRoutes[F[_]: JsonDecoder: Async](
    assets: AssetsService[F]
  ) extends Routes[F, AuthedUser] {
  override val path = "/assets"

  private val AllowedMediaTypes: List[MediaType] = List(
    MediaType.image.png,
    MediaType.image.jpeg,
    MediaType.unsafeParse("text/csv"),
    MediaType.unsafeParse("application/vnd.ms-excel"),
    MediaType.unsafeParse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
  )

  override val public: HttpRoutes[F] = HttpRoutes.empty[F]
  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {
    case ar @ POST -> Root as _ =>
      ar.req.decode[Multipart[F]] { multipart =>
        val fileParts = multipart.parts.fileParts(AllowedMediaTypes: _*)
        val fileMeta = fileParts.headOption.map { fp =>
          FileMeta(
            fp.body,
            fp.contentType.map(_.mediaType).map(m => s"${m.mainType}/${m.subType}"),
            fp.filename.getOrElse(""),
            fp.contentLength.getOrElse(0L),
          )
        }
        fileMeta
          .fold(AError.BadRequest("File part not found").raiseError[F, Response[F]])(
            assets.create(_).flatMap { assetId =>
              Created(ObjectId(assetId))
            }
          )
      }

  }
}
