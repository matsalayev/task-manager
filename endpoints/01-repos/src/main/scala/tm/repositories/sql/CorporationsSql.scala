package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.implicits._

import tm.domain.CorporateId
import tm.domain.corporate.Corporate
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes
import tm.support.skunk.codecs.zonedDateTime

private[repositories] object CorporationsSql extends Sql[CorporateId] {
  private val codec: Codec[Corporate] =
    (id *: zonedDateTime *: nes *: LocationsSql.id *: AssetsSql.id.opt).to[Corporate]

  val insert: Command[Corporate] =
    sql"""INSERT INTO corporations VALUES ($codec)""".command

  val findById: Query[CorporateId, Corporate] =
    sql"""SELECT * FROM corporations WHERE id = $id LIMIT 1""".query(codec)

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
