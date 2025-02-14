package tm.repositories.sql

import skunk._
import skunk.implicits._

import tm.domain.AssetId
import tm.domain.asset.Asset
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes
import tm.support.skunk.codecs.zonedDateTime

private[repositories] object AssetsSql extends Sql[AssetId] {
  private val codec: Codec[Asset] = (id *: zonedDateTime *: nes *: nes.opt *: nes.opt).to[Asset]

  val insert: Command[Asset] =
    sql"""INSERT INTO assets VALUES ($codec)""".command

  val findById: Query[AssetId, Asset] =
    sql"""SELECT * FROM assets WHERE id = $id LIMIT 1""".query(codec)
}
