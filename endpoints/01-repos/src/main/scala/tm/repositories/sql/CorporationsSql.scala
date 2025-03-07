package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.codec.all.float8
import skunk.implicits._

import tm.domain.CorporateId
import tm.domain.corporate.Corporate
import tm.repositories.dto
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes
import tm.support.skunk.codecs.zonedDateTime
private[repositories] object CorporationsSql extends Sql[CorporateId] {
  private val codec: Codec[Corporate] =
    (id *: zonedDateTime *: nes *: LocationsSql.id *: AssetsSql.id.opt).to[Corporate]
  private val dtoCodec: Codec[dto.Corporate] =
    (id *: zonedDateTime *: nes *: LocationsSql.id *: nes *: float8 *: float8 *: AssetsSql.id.opt)
      .to[dto.Corporate]

  val insert: Command[Corporate] =
    sql"""INSERT INTO corporations VALUES ($codec)""".command

  val findById: Query[CorporateId, dto.Corporate] =
    sql"""
      SELECT
        c.id,
        c.created_at,
        c.name,
        c.location_id,
        l.name,
        l.latitude,
        l.longitude,
        c.asset_id
      FROM corporations c
      INNER JOIN locations l
        ON l.id = c.location_id
      WHERE id = $id
      LIMIT 1
    """.query(dtoCodec)

  val findByName: Query[NonEmptyString, Corporate] =
    sql"""SELECT * FROM corporations WHERE name = $nes LIMIT 1""".query(codec)

  val update: Command[Corporate] =
    sql"""UPDATE corporations
      SET
        name = $nes,
        location_id = ${LocationsSql.id},
        asset_id = ${AssetsSql.id.opt}
      WHERE id = $id"""
      .command
      .contramap { (c: Corporate) =>
        c.name *: c.locationId *: c.photo *: c.id *: EmptyTuple
      }

  val delete: Command[CorporateId] =
    sql"""DELETE FROM corporations WHERE id = $id""".command
}
