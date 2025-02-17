package tm.repositories.sql

import shapeless.HNil
import skunk._
import skunk.implicits._

import tm.Phone
import tm.domain.PersonId
import tm.domain.auth.AccessCredentials
import tm.domain.auth.AuthedUser.User
import tm.domain.corporate
import tm.repositories.dto
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes
import tm.support.skunk.codecs.phone
import tm.support.skunk.codecs.zonedDateTime

private[repositories] object UsersSql extends Sql[PersonId] {
  private val codec = (id *: role *: phone).to[User]
  private val corporateUserCodec =
    (id *: role *: phone *: AssetsSql.id.opt *: CorporationsSql.id).to[corporate.User]
  private val dtoUserCodec =
    (id *: zonedDateTime *: nes *: CorporationsSql.id *: nes *: role *: AssetsSql.id.opt *: phone)
      .to[dto.User]

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

  val findById: Query[PersonId, dto.User] =
    sql"""
      SELECT
        u.id, u.created_at, p.full_name, u.corporate_id, c.name, u.role, u.asset_id, u.phone
      FROM users u
      INNER JOIN people p
        ON p.id = u.id
      INNER JOIN corporates c
        ON c.id = u.corporate_id
      WHERE
        u.id = $id
        AND deleted_at IS NULL
    """.query(dtoUserCodec)

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
