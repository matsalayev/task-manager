package tm.repositories.sql

import skunk._
import skunk.implicits._

import tm.Phone
import tm.domain.EmployeeId
import tm.domain.PersonId
import tm.domain.employee.Employee
import tm.repositories.dto
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes
import tm.support.skunk.codecs.phone
import tm.support.skunk.codecs.zonedDateTime

private[repositories] object EmployeesSql extends Sql[EmployeeId] {
  private val codec: Codec[Employee] =
    (id *: zonedDateTime *: PeopleSql.id *: CorporationsSql.id *: SpecialtiesSql.id *: AssetsSql
      .id
      .opt *: phone)
      .to[Employee]

  private val dtoCodec: Codec[dto.Employee] =
    (id *: zonedDateTime *: PeopleSql.id *: nes *: CorporationsSql.id *: nes *: SpecialtiesSql.id *: nes *:
      AssetsSql.id.opt *: phone).to[dto.Employee]

  val insert: Command[Employee] =
    sql"""INSERT INTO employees VALUES ($codec)""".command

  val findById: Query[PersonId, dto.Employee] =
    sql"""
      SELECT
        e.id,
        e.created_at,
        e.person_id,
        p.full_name,
        e.corporate_id,
        c.name,
        e.specialty_id,
        s.name,
        e.asset_id,
        e.phone
      FROM employees e
      INNER JOIN people p
        ON p.id = e.person_id
      INNER JOIN corporations c
        ON c.id = e.corporate_id
      INNER JOIN specialties s
        ON s.id = e.specialty_id
      WHERE e.person_id = ${PeopleSql.id}
      LIMIT 1
    """.query(dtoCodec)

  val findByPhone: Query[Phone, dto.Employee] =
    sql"""
      SELECT
        e.id,
        e.created_at,
        e.person_id,
        p.full_name,
        e.corporate_id,
        c.name,
        e.specialty_id,
        s.name,
        e.asset_id,
        e.phone
      FROM employees e
      INNER JOIN people p
        ON p.id = e.person_id
      INNER JOIN corporations c
        ON c.id = e.corporate_id
      INNER JOIN specialties s
        ON s.id = e.specialty_id
      WHERE e.phone = $phone
      LIMIT 1
    """.query(dtoCodec)

  val update: Command[Employee] =
    sql"""UPDATE employees
      SET
        corporate_id = ${CorporationsSql.id},
        specialty_id = ${SpecialtiesSql.id},
        asset_id = ${AssetsSql.id.opt},
        phone = $phone
      WHERE id = $id"""
      .command
      .contramap { (e: Employee) =>
        e.corporateId *: e.specialtyId *: e.photo *: e.phone *: e.id *: EmptyTuple
      }

  val delete: Command[EmployeeId] =
    sql"""DELETE FROM employees WHERE id = $id""".command
}
