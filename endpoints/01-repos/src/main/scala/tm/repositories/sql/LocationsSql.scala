package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.codec.all.float8
import skunk.implicits._

import tm.domain.LocationId
import tm.domain.corporate.Location
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes

private[repositories] object LocationsSql extends Sql[LocationId] {
  private val codec: Codec[Location] = (id *: nes *: float8 *: float8).to[Location]

  val insert: Command[Location] =
    sql"""INSERT INTO locations VALUES ($codec)""".command

  val findById: Query[LocationId, Location] =
    sql"""SELECT * FROM locations WHERE id = $id LIMIT 1""".query(codec)

  val findByName: Query[NonEmptyString, Location] =
    sql"""SELECT * FROM locations WHERE name = $nes LIMIT 1""".query(codec)

  val update: Command[Location] =
    sql"""UPDATE locations
      SET
        name = $nes,
        latitude = $float8,
        longitude = $float8
      WHERE id = $id"""
      .command
      .contramap { (l: Location) =>
        l.name *: l.latitude *: l.longitude *: l.id *: EmptyTuple
      }

  val delete: Command[LocationId] =
    sql"""DELETE FROM locations WHERE id = $id""".command
}
