package tm.repositories

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import skunk._

import tm.Phone
import tm.domain.EmployeeId
import tm.domain.PersonId
import tm.domain.SpecialtyId
import tm.domain.employee.Employee
import tm.domain.employee.Specialty
import tm.repositories.sql.EmployeesSql
import tm.repositories.sql.SpecialtiesSql
import tm.support.skunk.syntax.all._

trait EmployeeRepository[F[_]] {
  def create(employee: Employee): F[Unit]
  def findById(personId: PersonId): F[Option[dto.Employee]]
  def findByPhone(phone: Phone): F[Option[dto.Employee]]
  def update(employee: Employee): F[Unit]
  def delete(employeeId: EmployeeId): F[Unit]
  def createRank(specialty: Specialty): F[Unit]
  def findRankById(specialtyId: SpecialtyId): F[Option[Specialty]]
  def findRankByName(name: NonEmptyString): F[Option[Specialty]]
  def updateRank(specialty: Specialty): F[Unit]
  def deleteRank(specialtyId: SpecialtyId): F[Unit]
}

object EmployeeRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): EmployeeRepository[F] = new EmployeeRepository[F] {
    override def create(employee: Employee): F[Unit] =
      EmployeesSql.insert.execute(employee)

    override def findById(personId: PersonId): F[Option[dto.Employee]] =
      EmployeesSql.findById.queryOption(personId)

    override def findByPhone(phone: Phone): F[Option[dto.Employee]] =
      EmployeesSql.findByPhone.queryOption(phone)

    override def update(employee: Employee): F[Unit] = EmployeesSql.update.execute(employee)

    override def delete(employeeId: EmployeeId): F[Unit] =
      EmployeesSql.delete.execute(employeeId)

    override def createRank(specialty: Specialty): F[Unit] =
      SpecialtiesSql.insert.execute(specialty)

    override def findRankById(specialtyId: SpecialtyId): F[Option[Specialty]] =
      SpecialtiesSql.findById.queryOption(specialtyId)

    override def findRankByName(name: NonEmptyString): F[Option[Specialty]] =
      SpecialtiesSql.findByName.queryOption(name)

    override def updateRank(specialty: Specialty): F[Unit] =
      SpecialtiesSql.update.execute(specialty)

    override def deleteRank(specialtyId: SpecialtyId): F[Unit] =
      SpecialtiesSql.delete.execute(specialtyId)
  }
}
