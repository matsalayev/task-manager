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
import tm.domain.telegram.Contact
import tm.domain.telegram.TelegramCallbackQuery
import tm.domain.telegram.TelegramMessage
import tm.domain.telegram.Update
import tm.effects.Calendar
import tm.integrations.telegram.TelegramClient
import tm.integrations.telegram.domain.InlineKeyboardButton
import tm.integrations.telegram.domain.KeyboardButton
import tm.integrations.telegram.domain.ReplyMarkup.ReplyInlineKeyboardMarkup
import tm.integrations.telegram.domain.ReplyMarkup.ReplyKeyboardMarkup
import tm.integrations.telegram.domain.ReplyMarkup.ReplyKeyboardRemove
import tm.repositories.TelegramBotUsersRepository
import tm.syntax.refined.commonSyntaxAutoRefineV

trait TelegramService[F[_]] {
  def telegramMessage(update: Update): F[Unit]
}

object TelegramService {
  def make[F[_]: Monad: Calendar](
      telegramClient: TelegramClient[F],
      telegramBotUsersRepository: TelegramBotUsersRepository[F],
    )(implicit
      logger: Logger[F]
    ): TelegramService[F] = new TelegramService[F] {
//    override def telegramMessage(update: Update): F[Unit] =
//      update match {
//        case Update(_, Some(TelegramMessage(_, Some(user), Some(text), None)), _) =>
//          text match {
//            case "/start" => sendContactRequest(user.id)
//            case "/students" => sendStudents(user.id)
//            case _ => logger.info(s"undefined behaviour for customer bot")
//          }
//        case Update(
//               _,
//               Some(
//                 TelegramMessage(
//                   _,
//                   Some(user),
//                   None,
//                   Some(Contact(phoneNumberStr, Some(userTelegramId))),
//                 )
//               ),
//               _,
//             ) if user.id == userTelegramId =>
//          val phoneNumber: Phone =
//            if (phoneNumberStr.startsWith("+")) phoneNumberStr else s"+$phoneNumberStr"
//
//          parentsRepository.findByPhone(phoneNumber).flatMap {
//            case Some(parent) =>
//              for {
//                _ <- telegramClient.sendMessage(
//                  user.id,
//                  s"Assalomu alaykum ${user.firstName} ${user.lastName.getOrElse("")}",
//                  ReplyKeyboardRemove().some,
//                )
//                _ <- saveBotUser(user.id, parent.id)
//                _ <- sendStudents(user.id)
//              } yield ()
//            case _ =>
//              telegramClient.sendMessage(
//                user.id,
//                s"Uzr ${user.firstName} sizning '$phoneNumberStr' raqamingizni ota-onalar orasidan topa olmadik!",
//                ReplyKeyboardRemove().some,
//              )
//          }
//
//        case Update(_, _, Some(TelegramCallbackQuery(Some(user), _, Some(message), Some(data)))) =>
//          val regexData =
//            """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})__(\d{2})__(\d{2})""".r
//          val regexWeek =
//            """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})__(\d{2})""".r
//          val regexMonth =
//            """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})""".r
//          data.value match {
//            case regexData(id, month, week) =>
//              val backButton = List(InlineKeyboardButton("⬅ Orqaga", id + "__" + month))
//              studentsRepository
//                .findById(PersonId(UUID.fromString(id)))
//                .flatMap(studentOpt =>
//                  studentOpt.fold(Applicative[F].unit) { student =>
//                    for {
//
//                      _ <- telegramClient
//                        .editMessageText(
//                          chatId = user.id,
//                          messageId = message.messageId,
//                          text =
//                            s"O'quvchi : ${student.fullName}\nOy : $month\nHafta : $week\nBoshqa haftalarni ko'rish uchun orqaga qayting.",
//                        )
//                      _ <- telegramClient
//                        .editMessageReplyMarkup(
//                          chatId = user.id,
//                          messageId = message.messageId,
//                          replyMarkup = ReplyInlineKeyboardMarkup(List(backButton)).some,
//                        )
//                      _ <- telegramClient.sendMessage(
//                        chatId = user.id,
//                        text = "Ma'lumot topilmadi.",
//                      )
//                    } yield ()
//                  }
//                )
//            case regexWeek(id, month) =>
//              val weeks = List("1⃣ - Hafta", "2⃣ - Hafta", "3⃣ - Hafta", "4⃣ - Hafta")
//              val weekNumbers = (1 to 4).map(n => f"$n%02d").toList
//
//              val keyboard = weeks
//                .zip(weekNumbers)
//                .grouped(2)
//                .map { pair =>
//                  pair.map {
//                    case (week, number) =>
//                      InlineKeyboardButton(week, s"${data.value}__$number")
//                  }
//                }
//                .toList
//
//              val backButton = List(InlineKeyboardButton("⬅ Orqaga", id))
//
//              studentsRepository
//                .findById(PersonId(UUID.fromString(id)))
//                .flatMap(studentOpt =>
//                  studentOpt.fold(Applicative[F].unit) { student =>
//                    telegramClient
//                      .editMessageText(
//                        chatId = user.id,
//                        messageId = message.messageId,
//                        text = s"O'quvchi : ${student.fullName}\nOy : $month\nHaftani tanlang :",
//                      )
//                      .flatMap(_ =>
//                        telegramClient.editMessageReplyMarkup(
//                          chatId = user.id,
//                          messageId = message.messageId,
//                          replyMarkup = ReplyInlineKeyboardMarkup(keyboard :+ backButton).some,
//                        )
//                      )
//                  }
//                )
//            case regexMonth(id) =>
//              val months = List(
//                "Yanvar",
//                "Fevral",
//                "Mart",
//                "Aprel",
//                "May",
//                "Iyun",
//                "Iyul",
//                "Avgust",
//                "Sentyabr",
//                "Oktyabr",
//                "Noyabr",
//                "Dekabr",
//              )
//              val monthNumbers = (1 to 12).map(n => f"$n%02d").toList
//              val keyboard = months
//                .zip(monthNumbers)
//                .grouped(3)
//                .map { pair =>
//                  pair.map {
//                    case (month, number) =>
//                      InlineKeyboardButton(month, s"${data.value}__$number")
//                  }
//                }
//                .toList
//              studentsRepository
//                .findById(PersonId(UUID.fromString(id)))
//                .flatMap(studentOpt =>
//                  studentOpt.fold(Applicative[F].unit) { student =>
//                    telegramClient
//                      .editMessageText(
//                        chatId = user.id,
//                        messageId = message.messageId,
//                        text = s"O'quvchi : ${student.fullName}\nOyni tanlang :",
//                      )
//                      .flatMap(_ =>
//                        telegramClient.editMessageReplyMarkup(
//                          chatId = user.id,
//                          messageId = message.messageId,
//                          replyMarkup = ReplyInlineKeyboardMarkup(keyboard).some,
//                        )
//                      )
//                  }
//                )
//
//            case _ => logger.warn(s"unknown data type")
//          }
//        case _ => logger.info(s"unknown update type")
//      }
    private def sendContactRequest(chatId: Long): F[Unit] =
      telegramClient.sendMessage(
        chatId,
        "Iltimos raqamingizni yuboring.",
        ReplyKeyboardMarkup(
          List(List(KeyboardButton("Raqam yuborish", requestContact = true)))
        ).some,
      )

//    private def sendStudents(chatId: Long): F[Unit] =
//      telegramBotUsersRepository
//        .findByChatId(chatId)
//        .flatMap(personOpt =>
//          personOpt.fold(Applicative[F].unit) { personId =>
//            for {
//              studentNames <- studentsRepository.findByParentId(personId)
//              keyboard = studentNames.map { student =>
//                List(InlineKeyboardButton(student.fullName.value, student.id.value.toString))
//              }
//              _ <- telegramClient.sendMessage(
//                chatId = chatId,
//                text = "Quyidagi o'quvchilardan birini tanlang:",
//                replyMarkup = ReplyInlineKeyboardMarkup(keyboard).some,
//              )
//            } yield ()
//          }
//        )

    private def saveBotUser(chatId: Long, personId: PersonId): F[Unit] =
      telegramBotUsersRepository
        .findByChatId(chatId)
        .flatMap(personOpt =>
          personOpt.fold(telegramBotUsersRepository.create(BotUser(personId, chatId)))(_ =>
            Applicative[F].unit
          )
        )

    override def telegramMessage(update: Update): F[Unit] = logger.info(s"$update")
  }
}
