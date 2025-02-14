package tm.repositories

import tm.database.DBSuite
import tm.domain.asset.Asset
import tm.generators.AssetGenerators
import tm.generators.Generators

object AssetRepositorySpec extends DBSuite with Generators with AssetGenerators {
  override def schemaName: String = "public"

  test("create asset") { implicit session =>
    val asset: Asset = assetGen.gen
    val assetRepo = AssetsRepository.make[F]
    for {
      _ <- assetRepo.create(asset)
      assetInDb <- assetRepo.findAsset(asset.id)
    } yield assert(assetInDb.exists(_.id == asset.id))
  }
}
