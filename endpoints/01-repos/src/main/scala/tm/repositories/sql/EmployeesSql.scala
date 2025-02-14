package tm.repositories.sql

import skunk._
import skunk.implicits._

import tm.Phone
import tm.domain.EmployeeId
import tm.domain.employee.Employee
import tm.support.skunk.Sql
import tm.support.skunk.codecs.phone
import tm.support.skunk.codecs.zonedDateTime

private[repositories] object EmployeesSql extends Sql[EmployeeId] {
  private val codec: Codec[Employee] =
    (id *: zonedDateTime *: PeopleSql.id *: CorporationsSql.id *: RanksSql.id *: AssetsSql.id.opt *: phone)
      .to[Employee]

  val insert: Command[Employee] =
    sql"""INSERT INTO employees VALUES ($codec)""".command

  val findById: Query[EmployeeId, Employee] =
    sql"""SELECT * FROM employees WHERE id = $id LIMIT 1""".query(codec)

  val findByPhone: Query[Phone, Employee] =
    sql"""SELECT * FROM employees WHERE phone = $phone LIMIT 1""".query(codec)

  val update: Command[Employee] =
    sql"""UPDATE employees
      SET
        corporate_id = ${CorporationsSql.id},
        rank_id = ${RanksSql.id},
        asset_id = ${AssetsSql.id.opt},
        phone = $phone
      WHERE id = $id"""
      .command
      .contramap { (e: Employee) =>
        e.corporateId *: e.rankId *: e.photo *: e.phone *: e.id *: EmptyTuple
      }

  val delete: Command[EmployeeId] =
    sql"""DELETE FROM employees WHERE id = $id""".command
}
