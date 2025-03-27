package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.codec.all.int8
import skunk.implicits._

import tm.domain.PersonId
import tm.domain.telegram.BotUser
import tm.repositories.dto.User
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes

private[repositories] object TelegramSql extends Sql {
  private val columns = PeopleSql.id *: int8

  val codec: Codec[BotUser] = columns.to[BotUser]

  val insertBotUser: Command[BotUser] =
    sql"""INSERT INTO telegram_bot_users VALUES ($codec)""".command

  val findById: Query[Long, PersonId] =
    sql"""SELECT person_id FROM telegram_bot_users WHERE chat_id = $int8""".query(PeopleSql.id)

  val findCorporateName: Query[Long, NonEmptyString] =
    sql"""
      SELECT c.name
      FROM telegram_bot_users tbu
      INNER JOIN users u
        ON u.id = tbu.person_id
      INNER JOIN corporations c
      	ON c.id = u.corporate_id
      WHERE tbu.chat_id = $int8
      LIMIT 1
    """.query(nes)

  val findUser: Query[Long, User] =
    sql"""
      SELECT
        p.id,
        u.created_at,
        p.full_name,
        c.id,
        c.name,
        u.role,
        u.asset_id,
        u.phone
      FROM users u
      INNER JOIN people p
        ON p.id = u.id
      INNER JOIN corporations c
        ON c.id = u.corporate_id
      INNER JOIN telegram_bot_users tbu
        ON  u.id = tbu.person_id
      WHERE tbu.chat_id = $int8
      LIMIT 1
    """.query(UsersSql.dtoUserCodec)
}
