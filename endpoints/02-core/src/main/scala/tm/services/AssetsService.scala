package tm.services

import cats.MonadThrow
import cats.implicits._

import tm.domain.AssetId
import tm.domain.asset.Asset
import tm.domain.asset.FileMeta
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.integration.aws.s3.S3Client
import tm.repositories.AssetsRepository
import tm.syntax.refined._
import tm.utils.ID

trait AssetsService[F[_]] {
  def create(meta: FileMeta[F]): F[AssetId]
}

object AssetsService {
  def make[F[_]: MonadThrow: GenUUID: Calendar: Lambda[M[_] => fs2.Compiler[M, M]]](
      assetsRepository: AssetsRepository[F],
      s3Client: S3Client[F],
    ): AssetsService[F] =
    new AssetsService[F] {
      override def create(meta: FileMeta[F]): F[AssetId] =
        for {
          id <- ID.make[F, AssetId]
          now <- Calendar[F].currentZonedDateTime
          key <- genFileKey(meta.fileName)
          asset = Asset(
            id = id,
            createdAt = now,
            s3Key = key,
            fileName = meta.fileName.some,
            contentType = meta.contentType,
          )
          _ <- meta.bytes.through(s3Client.putObject(key)).compile.drain
          _ <- assetsRepository.create(asset)
        } yield id

      private def getFileType(filename: String): String = {
        val extension = filename.substring(filename.lastIndexOf('.') + 1)
        extension.toLowerCase
      }

      private def genFileKey(orgFilename: String): F[String] =
        GenUUID[F].make.map { uuid =>
          uuid.toString + "." + getFileType(orgFilename)
        }
    }
}
