package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.implicits._

import tm.domain.SpecialtyId
import tm.domain.employee.Specialty
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes

private[repositories] object SpecialtiesSql extends Sql[SpecialtyId] {
  private val codec: Codec[Specialty] = (id *: nes *: CorporationsSql.id).to[Specialty]

  val insert: Command[Specialty] =
    sql"""INSERT INTO specialties VALUES ($codec)""".command

  val findById: Query[SpecialtyId, Specialty] =
    sql"""SELECT * FROM specialties WHERE id = $id LIMIT 1""".query(codec)

  val findByName: Query[NonEmptyString, Specialty] =
    sql"""SELECT * FROM specialties WHERE name = $nes LIMIT 1""".query(codec)

  val update: Command[Specialty] =
    sql"""UPDATE specialties
      SET name = $nes
      WHERE id = $id"""
      .command
      .contramap { (r: Specialty) =>
        r.name *: r.id *: EmptyTuple
      }

  val delete: Command[SpecialtyId] =
    sql"""DELETE FROM specialties WHERE id = $id""".command
}
