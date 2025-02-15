package tm.services

import java.util.UUID

import cats.Applicative
import cats.Monad
import cats.implicits.catsSyntaxOptionId
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import org.typelevel.log4cats.Logger

import tm.Phone
import tm.domain.PersonId
import tm.domain.telegram.BotUser
import tm.domain.telegram.CallbackQuery
import tm.domain.telegram.Contact
import tm.domain.telegram.Message
import tm.domain.telegram.Update
import tm.effects.Calendar
import tm.integrations.telegram.TelegramClient
import tm.integrations.telegram.domain.InlineKeyboardButton
import tm.integrations.telegram.domain.KeyboardButton
import tm.integrations.telegram.domain.ReplyMarkup.ReplyInlineKeyboardMarkup
import tm.integrations.telegram.domain.ReplyMarkup.ReplyKeyboardMarkup
import tm.integrations.telegram.domain.ReplyMarkup.ReplyKeyboardRemove
import tm.repositories.CorporationsRepository
import tm.repositories.EmployeeRepository
import tm.repositories.ProjectsRepository
import tm.repositories.TelegramRepository
import tm.syntax.refined.commonSyntaxAutoRefineV

trait CorporateBotService[F[_]] {
  def telegramMessage(update: Update): F[Unit]
}

object CorporateBotService {
  def make[F[_]: Monad: Calendar](
      telegramClient: TelegramClient[F],
      telegramRepository: TelegramRepository[F],
      employeeRepository: EmployeeRepository[F],
      corporationsRepository: CorporationsRepository[F],
      projectsRepository: ProjectsRepository[F],
    )(implicit
      logger: Logger[F]
    ): CorporateBotService[F] = new CorporateBotService[F] {
    override def telegramMessage(update: Update): F[Unit] =
      update match {
        case Update(_, Some(Message(_, Some(user), _, Some(text), None, _, _, _, _)), _) =>
          text match {
            case "/start" => sendContactRequest(user.id)
            case "/me" => sendEmployeeInfo(user.id)
            case "/corporate" => sendCorporateInfo(user.id)
            case "/projects" => sendProjects(user.id)
            case _ => logger.info(s"undefined behaviour for customer bot")
          }
        case Update(
               _,
               Some(
                 Message(
                   _,
                   Some(user),
                   _,
                   None,
                   Some(Contact(phoneNumberStr, Some(userTelegramId))),
                   _,
                   _,
                   _,
                   _,
                 )
               ),
               _,
             ) if user.id == userTelegramId =>
          val phoneNumber: Phone =
            if (phoneNumberStr.startsWith("+")) phoneNumberStr else s"+$phoneNumberStr"

          employeeRepository.findByPhone(phoneNumber).flatMap {
            case Some(employee) =>
              saveBotUser(user.id, employee.personId).flatMap(_ => sendEmployeeInfo(user.id))
            case _ =>
              telegramClient.sendMessage(
                user.id,
                s"Uzr ${user.firstName} sizning '$phoneNumberStr' raqamingizni ota-onalar orasidan topa olmadik!",
                ReplyKeyboardRemove().some,
              )
          }

        case Update(_, _, Some(CallbackQuery(Some(user), _, Some(message), Some(data)))) =>
          val regexData =
            """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})__(\d{2})__(\d{2})""".r
          val regexWeek =
            """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})__(\d{2})""".r
          val regexMonth =
            """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})""".r
          data.value match {
            case _ => logger.warn(s"unknown data type")
          }
        case _ => logger.info(s"unknown update type")
      }

    private def sendContactRequest(chatId: Long): F[Unit] =
      telegramClient.sendMessage(
        chatId,
        "Iltimos raqamingizni yuboring.",
        ReplyKeyboardMarkup(
          List(List(KeyboardButton("Raqam yuborish", requestContact = true)))
        ).some,
      )

    private def sendEmployeeInfo(chatId: Long): F[Unit] =
      telegramRepository.findByChatId(chatId).flatMap {
        case Some(personId) =>
          employeeRepository.findById(personId).flatMap {
            case Some(employee) =>
              for {
                _ <- telegramClient.sendMessage(
                  chatId,
                  s"Assalomu alaykum ${employee.fullName}\nKorporatsiya: ${employee.corporateName}\nLavozim: ${employee.specialtyName}",
                  ReplyKeyboardRemove().some,
                )
              } yield ()
            case _ =>
              telegramClient.sendMessage(
                chatId,
                s"Uzr sizni xodimlar orasidan topa olmadik!",
                ReplyKeyboardRemove().some,
              )
          }
        case _ =>
          telegramClient.sendMessage(
            chatId,
            s"Uzr sizni foydalanuvchilar orasidan topa olmadik!",
            ReplyKeyboardRemove().some,
          )
      }

    private def sendCorporateInfo(chatId: Long): F[Unit] =
      telegramRepository.findByChatId(chatId).flatMap {
        case Some(personId) =>
          employeeRepository.findById(personId).flatMap {
            case Some(employee) =>
              corporationsRepository.findById(employee.corporateId).flatMap {
                case Some(corporate) =>
                  telegramClient.sendMessage(
                    chatId,
                    s"Korporatsiya: ${corporate.name}\nJoylashuv: ${corporate.locationId}",
                    ReplyKeyboardRemove().some,
                  )
                case _ => Applicative[F].unit
              }
            case _ =>
              telegramClient.sendMessage(
                chatId,
                s"Uzr sizni xodimlar orasidan topa olmadik!",
                ReplyKeyboardRemove().some,
              )
          }
        case _ =>
          telegramClient.sendMessage(
            chatId,
            s"Uzr sizni foydalanuvchilar orasidan topa olmadik!",
            ReplyKeyboardRemove().some,
          )
      }

    private def sendProjects(chatId: Long): F[Unit] =
      telegramRepository.findByChatId(chatId).flatMap {
        case Some(personId) =>
          employeeRepository.findById(personId).flatMap {
            case Some(employee) =>
              projectsRepository.getAll(employee.corporateId).flatMap { projects =>
                Applicative[F].unit
              //                case Some(corporate) =>
              //                  telegramClient.sendMessage(
              //                    chatId,
              //                    s"Korporatsiya: ${corporate.name}\nJoylashuv: ${corporate.locationId}",
              //                    ReplyKeyboardRemove().some,
              //                  )
              //                case _ => Applicative[F].unit
              }
            case _ =>
              telegramClient.sendMessage(
                chatId,
                s"Uzr sizni xodimlar orasidan topa olmadik!",
                ReplyKeyboardRemove().some,
              )
          }
        case _ =>
          telegramClient.sendMessage(
            chatId,
            s"Uzr sizni foydalanuvchilar orasidan topa olmadik!",
            ReplyKeyboardRemove().some,
          )
      }

    private def saveBotUser(chatId: Long, personId: PersonId): F[Unit] =
      telegramRepository
        .findByChatId(chatId)
        .flatMap(personOpt =>
          personOpt.fold(telegramRepository.createBotUser(BotUser(personId, chatId)))(_ =>
            Applicative[F].unit
          )
        )
  }
}
