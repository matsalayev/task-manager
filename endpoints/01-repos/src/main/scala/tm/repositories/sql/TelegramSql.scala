package tm.repositories.sql

import skunk._
import skunk.codec.all.int8
import skunk.implicits._

import tm.domain.PersonId
import tm.domain.telegram.BotUser
import tm.support.skunk.Sql

private[repositories] object TelegramSql extends Sql {
  private val columns = PeopleSql.id *: int8

  val codec: Codec[BotUser] = columns.to[BotUser]

  val insertBotUser: Command[BotUser] =
    sql"""INSERT INTO telegram_bot_users VALUES ($codec)""".command

  val findById: Query[Long, PersonId] =
    sql"""SELECT person_id FROM telegram_bot_users WHERE chat_id = $int8""".query(PeopleSql.id)
}
