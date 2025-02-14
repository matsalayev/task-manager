package tm.repositories

import cats.effect.Resource
import skunk._

import tm.domain.AssetId
import tm.domain.asset.Asset
import tm.repositories.sql.AssetsSql
import tm.support.skunk.syntax.all._

trait AssetsRepository[F[_]] {
  def create(asset: Asset): F[Unit]
  def findAsset(assetId: AssetId): F[Option[Asset]]
}

object AssetsRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): AssetsRepository[F] = new AssetsRepository[F] {
    override def create(asset: Asset): F[Unit] =
      AssetsSql.insert.execute(asset)

    override def findAsset(assetId: AssetId): F[Option[Asset]] =
      AssetsSql.findById.queryOption(assetId)
  }
}
