package tm.services

import scala.concurrent.duration.DurationInt

import cats.Applicative
import cats.Monad
import cats.implicits.catsSyntaxOptionId
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import eu.timepit.refined.types.string.NonEmptyString
import org.typelevel.log4cats.Logger

import tm.Phone
import tm.domain.FolderId
import tm.domain.lite.Folder
import tm.domain.telegram.CallbackQuery
import tm.domain.telegram.Message
import tm.domain.telegram.Update
import tm.domain.telegram.User
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.integrations.telegram.TelegramClient
import tm.integrations.telegram.domain.InlineKeyboardButton
import tm.integrations.telegram.domain.KeyboardButton
import tm.integrations.telegram.domain.ReplyMarkup.ReplyInlineKeyboardMarkup
import tm.integrations.telegram.domain.ReplyMarkup.ReplyKeyboardMarkup
import tm.repositories.LiteTasksRepository
import tm.support.redis.RedisClient
import tm.syntax.refined.commonSyntaxAutoRefineV
import tm.utils.ID

trait LiteBotService[F[_]] {
  def telegramMessage(update: Update): F[Unit]
}

object LiteBotService {
  def make[F[_]: Monad: GenUUID: Calendar](
      telegramClient: TelegramClient[F],
      redisClient: RedisClient[F],
      liteTasksRepository: LiteTasksRepository[F],
    )(implicit
      logger: Logger[F]
    ): LiteBotService[F] = new LiteBotService[F] {
    override def telegramMessage(update: Update): F[Unit] =
      update match {
        case Update(_, Some(message), _) => handleMessage(message)
        case Update(_, _, Some(callbackQuery)) => handleCallbackQuery(callbackQuery)
        case _ => logger.info("unknown update type")
      }

    private def handleMessage(message: Message): F[Unit] =
      message match {
        case Message(messageId, Some(user), Some(text), None, None, None, None, None) =>
          handleTextMessage(messageId, user, text)
        case Message(_, Some(user), None, Some(contact), None, None, None, None) =>
          handleContactMessage(user, contact.phoneNumber)
        case Message(_, Some(user), None, None, Some(photos), _, mediaGroupId, None) =>
          Applicative[F].unit
        case Message(_, Some(user), None, None, None, None, None, Some(location)) =>
          Applicative[F].unit
        case _ => logger.info("undefined behaviour for customer bot")
      }

    private def handleTextMessage(
        messageId: Long,
        user: User,
        text: String,
      ): F[Unit] =
      text match {
        case "/start" => setButtons(user.id)
        case "/projects" => sendProjects(user.id)
        case "/folders" => sendFolders(user.id)
        case NonEmptyString(input) =>
          for {
            isFolder <- redisClient.get(user.id.toString + "+inputFolder")
            isTask <- redisClient.get(user.id.toString + "+inputTask")
            _ <- isFolder.fold(Applicative[F].unit) { oldMessageId =>
              createFolder(
                user.id,
                oldMessageId.toLong,
                messageId,
                input,
              )
            }
          } yield ()
        case _ => logger.info("unknown update type")
      }

    private def handleContactMessage(user: User, phoneNumberStr: String): F[Unit] = {
      val phoneNumber: Phone =
        if (phoneNumberStr.startsWith("+")) phoneNumberStr else s"+$phoneNumberStr"

      Applicative[F].unit

    }

    private def handleCallbackQuery(callbackQuery: CallbackQuery): F[Unit] =
      callbackQuery match {
        case CallbackQuery(Some(user), _, Some(message), Some(data)) =>
          handleCallbackData(user, message, data)
        case _ => logger.warn("unknown callback query structure")
      }

    private def handleCallbackData(
        user: User,
        message: Message,
        data: NonEmptyString,
      ): F[Unit] = {
      val regexFolder =
        """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})+folder""".r
      val regexTask =
        """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})+task""".r

      data.value match {
        case regexFolder(t) => ???
        case "createFolder" =>
          for {
            _ <- telegramClient.deleteMessage(chatId = user.id, messageId = message.messageId)
            _ <- telegramClient.sendMessage(
              chatId = user.id,
              text = "Iltimos jild nomini kiriting:",
            )
            _ <- redisClient.put(s"${user.id}+inputFolder", (message.messageId + 1).toString, 1.day)
          } yield ()
        case _ => logger.warn("unknown data type")
      }
    }

    private def setButtons(chatId: Long): F[Unit] =
      telegramClient.sendMessage(
        chatId,
        "Xayrli kun! /folders orqali jildlar yaratishingiz va ularda vazifalarni ularga sarflangan vaqtlar bo'yicha saqlashingiz mumkin!\n\nQuyidagi tugmalar yordamida esa tanlangan statusdagi vazifalarni ko'rashingiz mumkin!",
        ReplyKeyboardMarkup(
          List(
            List(KeyboardButton("TO DO")),
            List(KeyboardButton("IN PROGRESS")),
            List(KeyboardButton("IN REVIEW")),
            List(KeyboardButton("TESTING")),
            List(KeyboardButton("DONE")),
          )
        ).some,
      )

    private def sendProjects(chatId: Long): F[Unit] = ???

    private def sendFolders(chatId: Long): F[Unit] = for {
      folders <- liteTasksRepository.getAllFolders(chatId)
      buttons = folders.map { folder =>
        List(InlineKeyboardButton(folder.name.value, folder.id.toString+"_folder"))
      }
      _ <- telegramClient.sendMessage(
        chatId = chatId,
        text = "Quyidagi jildlardan birini tanlang:",
        replyMarkup =
          ReplyInlineKeyboardMarkup(buttons :+ List(InlineKeyboardButton("+", "createFolder"))).some,
      )
    } yield ()

    def createFolder(
        chatId: Long,
        oldMessageId: Long,
        newMessageId: Long,
        folderName: NonEmptyString,
      ): F[Unit] =
      for {
        _ <- telegramClient.deleteMessage(chatId = chatId, messageId = newMessageId)
        _ <- telegramClient.deleteMessage(chatId = chatId, messageId = oldMessageId)
        id <- ID.make[F, FolderId]
        now <- Calendar[F].currentZonedDateTime
        _ <- liteTasksRepository.createFolder(
          Folder(
            id = id,
            createdAt = now,
            userId = chatId,
            name = folderName,
          )
        )
        _ <- redisClient.del(chatId.toString + "+inputFolder")
        _ <- sendFolders(chatId)
      } yield ()
  }
}
