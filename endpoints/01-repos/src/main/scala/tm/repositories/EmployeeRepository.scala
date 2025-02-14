package tm.repositories

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import skunk._

import tm.Phone
import tm.domain.EmployeeId
import tm.domain.RankId
import tm.domain.employee.Employee
import tm.domain.employee.Rank
import tm.repositories.sql.EmployeesSql
import tm.repositories.sql.RanksSql
import tm.support.skunk.syntax.all._

trait EmployeeRepository[F[_]] {
  def create(employee: Employee): F[Unit]
  def findById(employeeId: EmployeeId): F[Option[Employee]]
  def findByPhone(phone: Phone): F[Option[Employee]]
  def update(employee: Employee): F[Unit]
  def delete(employeeId: EmployeeId): F[Unit]
  def createRank(rank: Rank): F[Unit]
  def findRankById(rankId: RankId): F[Option[Rank]]
  def findRankByName(name: NonEmptyString): F[Option[Rank]]
  def updateRank(rank: Rank): F[Unit]
  def deleteRank(rankId: RankId): F[Unit]
}

object EmployeeRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): EmployeeRepository[F] = new EmployeeRepository[F] {
    override def create(employee: Employee): F[Unit] =
      EmployeesSql.insert.execute(employee)

    override def findById(employeeId: EmployeeId): F[Option[Employee]] =
      EmployeesSql.findById.queryOption(employeeId)

    override def findByPhone(phone: Phone): F[Option[Employee]] =
      EmployeesSql.findByPhone.queryOption(phone)

    override def update(employee: Employee): F[Unit] = EmployeesSql.update.execute(employee)

    override def delete(employeeId: EmployeeId): F[Unit] =
      EmployeesSql.delete.execute(employeeId)

    override def createRank(rank: Rank): F[Unit] =
      RanksSql.insert.execute(rank)

    override def findRankById(rankId: RankId): F[Option[Rank]] =
      RanksSql.findById.queryOption(rankId)

    override def findRankByName(name: NonEmptyString): F[Option[Rank]] =
      RanksSql.findByName.queryOption(name)

    override def updateRank(rank: Rank): F[Unit] =
      RanksSql.update.execute(rank)

    override def deleteRank(rankId: RankId): F[Unit] =
      RanksSql.delete.execute(rankId)
  }
}
