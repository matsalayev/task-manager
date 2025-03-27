package tm.repositories

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import tm.domain.PersonId
import tm.domain.telegram.BotUser
import tm.effects.Calendar
import tm.repositories.dto.User
import tm.repositories.sql.TelegramSql
import tm.support.skunk.syntax.all.skunkSyntaxCommandOps
import tm.support.skunk.syntax.all.skunkSyntaxQueryOps

trait TelegramRepository[F[_]] {
  def createBotUser(user: BotUser): F[Unit]
  def findByChatId(chatId: Long): F[Option[PersonId]]
  def findUser(chatId: Long): F[Option[User]]
  def findCorporateName(chatId: Long): F[Option[NonEmptyString]]
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

    override def findCorporateName(chatId: Long): F[Option[NonEmptyString]] =
      TelegramSql.findCorporateName.queryOption(chatId)

    override def findUser(chatId: Long): F[Option[User]] =
      TelegramSql.findUser.queryOption(chatId)
  }
}
