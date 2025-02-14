package tm.repositories.sql

import shapeless.HNil
import skunk._
import skunk.implicits._

import tm.Phone
import tm.domain.PersonId
import tm.domain.auth.AccessCredentials
import tm.domain.auth.AuthedUser.User
import tm.support.skunk.Sql
import tm.support.skunk.codecs.phone

private[repositories] object UsersSql extends Sql[PersonId] {
  private val codec = (id *: role *: phone).to[User]

  private val personDecoder: Decoder[AccessCredentials[User]] =
    (codec *: passwordHash).map {
      case user *: hash *: HNil =>
        AccessCredentials(
          data = user,
          password = hash,
        )
    }

  val findByLogin: Query[Phone, AccessCredentials[User]] =
    sql"""
      SELECT
        id, role, phone, password
      FROM users
      WHERE
        phone = $phone
        AND deleted_at IS NULL
    """.query(personDecoder)

  val insert: Command[AccessCredentials[User]] =
    sql"""
      INSERT INTO users (id, role, phone, password)
      VALUES ($id, $role, $phone, $passwordHash)
    """
      .command
      .contramap { (u: AccessCredentials[User]) =>
        u.data.id *: u.data.role *: u.data.phone *: u.password *: EmptyTuple
      }

  val delete: Command[PersonId] =
    sql"""DELETE FROM users WHERE id = $id""".command
}
