package tm.repositories

import cats.effect.Resource
import skunk._
import tm.domain.PersonId
import tm.domain.telegram.BotUser
import tm.effects.Calendar
import tm.repositories.sql.TelegramBotUsersSql
import tm.support.skunk.syntax.all.skunkSyntaxCommandOps
import tm.support.skunk.syntax.all.skunkSyntaxQueryOps

trait TelegramBotUsersRepository[F[_]] {
  def create(user: BotUser): F[Unit]
  def findByChatId(chatId: Long): F[Option[PersonId]]
}

object TelegramBotUsersRepository {
  def make[F[_]: fs2.Compiler.Target: Calendar](
      implicit
      session: Resource[F, Session[F]]
    ): TelegramBotUsersRepository[F] = new TelegramBotUsersRepository[F] {
    override def create(user: BotUser): F[Unit] =
      TelegramBotUsersSql.insert.execute(user)

    override def findByChatId(chatId: Long): F[Option[PersonId]] =
      TelegramBotUsersSql.findById.queryOption(chatId)
  }
}
