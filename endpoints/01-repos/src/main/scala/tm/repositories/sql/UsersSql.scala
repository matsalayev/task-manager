package tm.repositories.sql

import shapeless.HNil
import skunk._
import skunk.implicits._

import tm.Phone
import tm.domain.PersonId
import tm.domain.auth.AccessCredentials
import tm.domain.auth.AuthedUser.User
import tm.domain.corporate
import tm.support.skunk.Sql
import tm.support.skunk.codecs.phone

private[repositories] object UsersSql extends Sql[PersonId] {
  private val codec = (id *: role *: phone).to[User]
  private val corporateUserCodec =
    (id *: role *: phone *: AssetsSql.id.opt *: CorporationsSql.id).to[corporate.User]

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

  val findByPhone: Query[Phone, corporate.User] =
    sql"""
      SELECT
        id, role, phone, asset_id, corporate_id
      FROM users
      WHERE
        phone = $phone
        AND deleted_at IS NULL
    """.query(corporateUserCodec)

  val insert: Command[AccessCredentials[User]] =
    sql"""
      INSERT INTO users (id, role, phone, password)
      VALUES ($id, $role, $phone, $passwordHash)
    """
      .command
      .contramap { (u: AccessCredentials[User]) =>
        u.data.id *: u.data.role *: u.data.phone *: u.password *: EmptyTuple
      }

  val createUser: Command[corporate.User] =
    sql"""
      INSERT INTO users (id, role, phone, asset_id, corporate_id)
      VALUES ($corporateUserCodec)
    """.command
//      .contramap { (u: corporate.User) =>
//        u.id *: u.role *: u.phone *: u.asset_id *: u.corporate_id *: EmptyTuple
//      }

  val delete: Command[PersonId] =
    sql"""DELETE FROM users WHERE id = $id""".command
}
