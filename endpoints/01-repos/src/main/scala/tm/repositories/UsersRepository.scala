package tm.repositories

import cats.effect.Resource
import skunk._

import tm.Phone
import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.auth.AccessCredentials
import tm.domain.auth.AuthedUser.User
import tm.domain.corporate
import tm.repositories.sql.UsersSql
import tm.support.skunk.syntax.all._

trait UsersRepository[F[_]] {
  def find(phone: Phone): F[Option[AccessCredentials[User]]]
  def create(userAndHash: AccessCredentials[User]): F[Unit]
  def createUser(user: corporate.User): F[Unit]
  def findByPhone(phone: Phone): F[Option[corporate.User]]
  def findById(id: PersonId): F[Option[dto.User]]
  def getCorporateUsers(id: CorporateId): F[List[corporate.User]]
}

object UsersRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): UsersRepository[F] = new UsersRepository[F] {
    override def find(phone: Phone): F[Option[AccessCredentials[User]]] =
      UsersSql.findByLogin.queryOption(phone)

    override def findByPhone(phone: Phone): F[Option[corporate.User]] =
      UsersSql.findByPhone.queryOption(phone)

    override def create(userAndHash: AccessCredentials[User]): F[Unit] =
      UsersSql.insert.execute(userAndHash)

    override def createUser(user: corporate.User): F[Unit] =
      UsersSql.createUser.execute(user)

    override def findById(id: PersonId): F[Option[dto.User]] =
      UsersSql.findById.queryOption(id)

    override def getCorporateUsers(id: CorporateId): F[List[corporate.User]] =
      UsersSql.get.queryList(id)
  }
}
