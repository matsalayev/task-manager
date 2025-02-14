package tm.repositories

import cats.effect.Resource
import skunk._
import tm.domain.PersonId
import tm.domain.telegram.BotUser
import tm.effects.Calendar
import tm.repositories.sql.TelegramSql
import tm.support.skunk.syntax.all.skunkSyntaxCommandOps
import tm.support.skunk.syntax.all.skunkSyntaxQueryOps

trait TelegramRepository[F[_]] {
  def createBotUser(user: BotUser): F[Unit]
  def findByChatId(chatId: Long): F[Option[PersonId]]
}

object TelegramRepository {
  def make[F[_]: fs2.Compiler.Target: Calendar](
      implicit
      session: Resource[F, Session[F]]
    ): TelegramRepository[F] = new TelegramRepository[F] {
    override def createBotUser(user: BotUser): F[Unit] =
      TelegramSql.insertBotUser.execute(user)

    override def findByChatId(chatId: Long): F[Option[PersonId]] =
      TelegramSql.findById.queryOption(chatId)
  }
}
