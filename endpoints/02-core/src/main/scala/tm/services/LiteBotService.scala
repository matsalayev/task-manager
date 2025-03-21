package tm.services

import java.util.UUID

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
import tm.domain.TaskId
import tm.domain.enums.TaskStatus
import tm.domain.lite.Folder
import tm.domain.lite.LiteTask
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
    private val regexFolder =
      """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})_(folder)""".r
    private val regexTask =
      """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})_(task)""".r
    private val regexCreateTask =
      """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})_(createTask)_(\d+)""".r
    private val regexCreateExitTask =
      """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})_(goBackToTasks)""".r
    private val regexPaginationError = """(pagination)_(\S+)""".r
    private val regexPagination = """(pagination)_(\S+)_(\d+)""".r

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
            _ <- isTask.fold(Applicative[F].unit) {
              case regexCreateTask(folderId, "createTask", oldMessageId) =>
                createTask(
                  user.id,
                  oldMessageId.toLong,
                  messageId,
                  FolderId(UUID.fromString(folderId)),
                  input,
                )

              case t => logger.info(s"$t")
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
        case CallbackQuery(id, Some(user), _, Some(message), Some(data)) =>
          handleCallbackData(id, user, message, data)
        case _ => logger.warn("unknown callback query structure")
      }

    private def handleCallbackData(
        callbackQueryId: String,
        user: User,
        message: Message,
        data: NonEmptyString,
      ): F[Unit] = {

      data.value match {
        case regexFolder(folderId, "folder") =>
          enterFolder(user.id, message.messageId, FolderId(UUID.fromString(folderId))).flatMap(_ =>
            redisClient.del(s"${user.id}+inputTask")
          )
        case "createFolder" =>
          for {
            _ <- telegramClient.editMessageText(
              user.id,
              message.messageId,
              "✍\uFE0F Iltimos jild nomini kiriting:",
            )
            _ <- telegramClient.editMessageReplyMarkup(
              user.id,
              message.messageId,
              ReplyInlineKeyboardMarkup(
                List(List(InlineKeyboardButton("❌ Bekor qilish", "goBackToFolders")))
              ).some,
            )
            _ <- redisClient.put(s"${user.id}+inputFolder", message.messageId.toString, 1.day)
          } yield ()
        case "goBackToFolders" =>
          for {
            _ <- redisClient.del(s"${user.id}+inputFolder")
            folders <- liteTasksRepository.getAllFolders(user.id, 5, 1)
            buttons = folders.data.map { folder =>
              List(
                InlineKeyboardButton(
                  "\uD83D\uDCC2 " + folder.name.value,
                  folder.id.toString + "_folder",
                )
              )
            }
            _ <- telegramClient.editMessageText(
              user.id,
              message.messageId,
              "\uD83D\uDDC2 Quyidagi jildlardan birini tanlang:",
            )
            _ <- telegramClient.editMessageReplyMarkup(
              chatId = user.id,
              messageId = message.messageId,
              replyMarkup = ReplyInlineKeyboardMarkup(
                buttons :+ List(
                  InlineKeyboardButton("⬅\uFE0F", "pagination_previous"),
                  InlineKeyboardButton("1⃣", "pagination_currentPage"),
                  InlineKeyboardButton("➡\uFE0F", "pagination_next"),
                ) :+ List(InlineKeyboardButton("➕", "createFolder"))
              ).some,
            )
          } yield ()
        case regexCreateTask(folderId, "createTask", msgId) =>
          for {
            _ <- telegramClient.editMessageText(
              user.id,
              message.messageId,
              "✍\uFE0F Iltimos vazifa nomini kiriting:",
            )
            _ <- telegramClient.editMessageReplyMarkup(
              user.id,
              message.messageId,
              ReplyInlineKeyboardMarkup(
                List(List(InlineKeyboardButton("❌ Bekor qilish", folderId + "_folder")))
              ).some,
            )
            _ <- redisClient.put(s"${user.id}+inputTask", folderId + "_createTask_" + msgId, 1.day)
          } yield ()
        case regexTask(taskIdStr, "task") =>
          val taskId = TaskId(UUID.fromString(taskIdStr))
          liteTasksRepository.findById(taskId).flatMap { taskOpt =>
            taskOpt.fold(
              telegramClient.sendMessage(user.id, "❌ Vazifa haqida ma'lumot topilmadi!")
            ) { task =>
              val buttons = ReplyInlineKeyboardMarkup(
                List(
                  List(
                    InlineKeyboardButton("▶\uFE0F", task.id.toString + "_play"),
                    InlineKeyboardButton("⏩", task.id.toString + "_next"),
                    InlineKeyboardButton("*⃣", task.id.toString + "_actions"),
                  ),
                  List(InlineKeyboardButton("↩\uFE0F", task.folderId.toString + "_goBackToTasks")),
                )
              )
              val emoji = task.status match {
                case TaskStatus.ToDo => "\uD83D\uDCDD "
                case TaskStatus.InProgress => "⏳ "
                case TaskStatus.InReview => "\uD83D\uDC41 "
                case TaskStatus.Testing => "⛓\uFE0F\u200D\uD83D\uDCA5 "
                case _ => ""
              }
              val info =
                "\n\n▶\uFE0F - 'start' ishni boshlashdan oldin bosing!\n⏩ - 'next' vazifa statusini keyingisiga o'zgartiradi.\n*⃣ - 'others' boshqa amallar uchun."
              telegramClient
                .editMessageText(user.id, message.messageId, emoji + task.name.value)
                .flatMap(_ =>
                  telegramClient.editMessageReplyMarkup(user.id, message.messageId, buttons.some)
                )
            }
          }
        case regexCreateExitTask(folderId, "goBackToTasks") =>
          enterFolder(user.id, message.messageId, FolderId(UUID.fromString(folderId)))

        case regexPaginationError("pagination", action) =>
          telegramClient.answerCallbackQuery(
            callbackQueryId = callbackQueryId,
            text = action,
            showAlert = true,
            cache_time = 3,
          )
        case _ => logger.warn("unknown data type")
      }
    }

    private def setButtons(chatId: Long): F[Unit] =
      telegramClient.sendMessage(
        chatId,
        "Xayrli kun! /folders orqali jildlar yaratishingiz va ularda vazifalarni ularga sarflangan vaqtlar bo'yicha saqlashingiz mumkin!\n\nQuyidagi tugmalar yordamida esa tanlangan statusdagi vazifalarni ko'rashingiz mumkin!",
        ReplyKeyboardMarkup(
          List(
            List(KeyboardButton("TO DO \uD83D\uDCDD")),
            List(KeyboardButton("IN PROGRESS ⏳")),
            List(KeyboardButton("IN REVIEW \uD83D\uDC41")),
            List(KeyboardButton("TESTING ⛓\uFE0F\u200D\uD83D\uDCA5"), KeyboardButton("DONE ✅")),
          )
        ).some,
      )

    private def sendFolders(chatId: Long): F[Unit] = for {
      folders <- liteTasksRepository.getAllFolders(chatId, 5, 1)
      buttons = folders.data.map { folder =>
        List(
          InlineKeyboardButton("\uD83D\uDCC2 " + folder.name.value, folder.id.toString + "_folder")
        )
      }
      _ <- telegramClient.sendMessage(
        chatId = chatId,
        text = "\uD83D\uDDC2 Quyidagi jildlardan birini tanlang:",
        replyMarkup = ReplyInlineKeyboardMarkup(
          buttons :+ List(
            InlineKeyboardButton("⬅\uFE0F", "pagination_previous"),
            InlineKeyboardButton("1⃣", "pagination_currentPage"),
            InlineKeyboardButton("➡\uFE0F", "pagination_next"),
          ) :+ List(InlineKeyboardButton("➕", "createFolder"))
        ).some,
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

        _ <- redisClient.del(s"$chatId+inputFolder")
        folders <- liteTasksRepository.getAllFolders(chatId, 5, 1)
        buttons = folders.data.map { folder =>
          List(
            InlineKeyboardButton(
              "\uD83D\uDCC2 " + folder.name.value,
              folder.id.toString + "_folder",
            )
          )
        }
        _ <- telegramClient.editMessageText(
          chatId,
          oldMessageId,
          "\uD83D\uDDC2 Quyidagi jildlardan birini tanlang:",
        )
        _ <- telegramClient.editMessageReplyMarkup(
          chatId = chatId,
          messageId = oldMessageId,
          replyMarkup = ReplyInlineKeyboardMarkup(
            buttons :+ List(
              InlineKeyboardButton("⬅\uFE0F", "pagination_previous"),
              InlineKeyboardButton("1⃣", "pagination_currentPage"),
              InlineKeyboardButton("➡\uFE0F", "pagination_next"),
            ) :+ List(InlineKeyboardButton("➕", "createFolder"))
          ).some,
        )

      } yield ()

    private def enterFolder(
        chatId: Long,
        messageId: Long,
        folderId: FolderId,
      ): F[Unit] =
      for {
        tasks <- liteTasksRepository.getAll(folderId, 5, 1)
        buttons = tasks.data.map { task =>
          val emoji = task.status match {
            case TaskStatus.ToDo => "\uD83D\uDCDD "
            case TaskStatus.InProgress => "⏳ "
            case TaskStatus.InReview => "\uD83D\uDC41 "
            case TaskStatus.Testing => "⛓\uFE0F\u200D\uD83D\uDCA5 "
            case _ => ""
          }
          List(InlineKeyboardButton(emoji + task.name.value, task.id.toString + "_task"))
        }
        _ <- telegramClient.editMessageText(
          chatId,
          messageId,
          "\uD83C\uDFAF Quyidagi vazifalardan birini tanlang:",
        )
        _ <- telegramClient.editMessageReplyMarkup(
          chatId,
          messageId,
          ReplyInlineKeyboardMarkup(
            buttons :+
              List(
                InlineKeyboardButton("⬅\uFE0F", "pagination_previous"),
                InlineKeyboardButton("1⃣", "pagination_currentPage"),
                InlineKeyboardButton("➡\uFE0F", "pagination_next"),
              ) :+
              List(
                InlineKeyboardButton("↩\uFE0F", "goBackToFolders"),
                InlineKeyboardButton("➕", folderId.toString + "_createTask_" + messageId),
              )
          ).some,
        )
      } yield ()

    private def sendTasks(
        chatId: Long,
        folderId: FolderId,
      ): F[Unit] =
      for {
        tasks <- liteTasksRepository.getAll(folderId, 5, 1)
        buttons = tasks.data.map { task =>
          val emoji = task.status match {
            case TaskStatus.ToDo => "\uD83D\uDCDD "
            case TaskStatus.InProgress => "⏳ "
            case TaskStatus.InReview => "\uD83D\uDC41 "
            case TaskStatus.Testing => "⛓\uFE0F\u200D\uD83D\uDCA5 "
            case _ => ""
          }
          List(InlineKeyboardButton(emoji + task.name.value, task.id.toString + "_task"))
        }
        _ <- telegramClient.sendMessage(
          chatId,
          "\uD83C\uDFAF Quyidagi vazifalardan birini tanlang:",
          ReplyInlineKeyboardMarkup(
            buttons :+
              List(
                InlineKeyboardButton("⬅\uFE0F", "pagination_previous"),
                InlineKeyboardButton("1⃣", "pagination_currentPage"),
                InlineKeyboardButton("➡\uFE0F", "pagination_next"),
              ) :+
              List(
                InlineKeyboardButton("↩\uFE0F", "goBackToFolders"),
                InlineKeyboardButton("➕", folderId.toString + "_createTask_" + 1),
              )
          ).some,
        )
      } yield ()

    def createTask(
        chatId: Long,
        oldMessageId: Long,
        newMessageId: Long,
        folderId: FolderId,
        taskName: NonEmptyString,
      ): F[Unit] =
      for {
        _ <- telegramClient.deleteMessage(chatId = chatId, messageId = newMessageId)
        id <- ID.make[F, TaskId]
        now <- Calendar[F].currentZonedDateTime
        _ <- liteTasksRepository.create(
          LiteTask(
            id = id,
            createdAt = now,
            userId = chatId,
            folderId = folderId,
            name = taskName,
            status = TaskStatus.ToDo,
            startedAt = None,
            finishedAt = None,
            duration = 0,
          )
        )
        _ <- redisClient.del(chatId.toString + "+inputTask")
        _ <- enterFolder(chatId, oldMessageId, folderId)
      } yield ()
  }
}
