package tm.services

import java.time.Duration
import java.util.UUID

import scala.concurrent.duration.DurationInt

import cats.Applicative
import cats.Monad
import cats.implicits.catsSyntaxOptionId
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import eu.timepit.refined.types.string.NonEmptyString
import org.typelevel.log4cats.Logger

import tm.domain.FolderId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus
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
import tm.support.redis.RedisClient
import tm.utils.ID
import tm.utils.Regex._

trait EmployeeService[F[_]] {
//  def telegramMessage(update: Update): F[Unit]
}
//
object EmployeeService {
  def make[F[_]: Monad: GenUUID: Calendar](
      telegramClient: TelegramClient[F],
      redisClient: RedisClient[F],
    )(implicit
      logger: Logger[F]
    ): EmployeeService[F] = new EmployeeService[F] {}
}
//    private val limit = 5
//
//    override def telegramMessage(update: Update): F[Unit] =
//      update match {
//        case Update(
//               _,
//               Some(Message(messageId, Some(user), Some(text), None, None, None, None, None)),
//               _,
//             ) =>
//          handleTextMessage(messageId, user, text)
//        case Update(_, _, Some(CallbackQuery(id, Some(user), _, Some(message), Some(data)))) =>
//          handleCallbackData(id, user, message, data)
//        case _ => logger.info("unknown update type")
//      }
//
//    private def handleTextMessage(
//        messageId: Long,
//        user: User,
//        text: String,
//      ): F[Unit] =
//      text match {
//        case "/start" => setButtons(user.id)
//        case "/folders" => sendFolders(user.id)
//        case NonEmptyString(input) =>
//          for {
//            isFolder <- redisClient.get(user.id.toString + "+inputFolder")
//            isTask <- redisClient.get(user.id.toString + "+inputTask")
//            _ <- isFolder.fold(Applicative[F].unit) {
//              case regexDigits(oldMessageId, page) =>
//                createFolder(
//                  user.id,
//                  oldMessageId.toLong,
//                  messageId,
//                  input,
//                  page.toInt,
//                )
//            }
//            _ <- isTask.fold(Applicative[F].unit) {
//              case regexUUIDWithActionAndDigitsAndPage(
//                     folderId,
//                     "createTask",
//                     oldMessageId,
//                     page,
//                     folderPage,
//                   ) =>
//                createTask(
//                  user.id,
//                  oldMessageId.toLong,
//                  messageId,
//                  FolderId(UUID.fromString(folderId)),
//                  input,
//                  page.toInt,
//                  folderPage.toInt,
//                )
//
//              case t => logger.info(s"$t")
//            }
//
//          } yield ()
//        case _ => logger.info("unknown update type")
//      }
//
//    private def handleCallbackData(
//        callbackQueryId: String,
//        user: User,
//        message: Message,
//        data: NonEmptyString,
//      ): F[Unit] =
//      data.value match {
//        case regexActionWithPage("createFolder", page) =>
//          for {
//            _ <- telegramClient.editMessageText(
//              user.id,
//              message.messageId,
//              "✍\uFE0F Iltimos jild nomini kiriting:",
//            )
//            _ <- telegramClient.editMessageReplyMarkup(
//              user.id,
//              message.messageId,
//              ReplyInlineKeyboardMarkup(
//                List(List(InlineKeyboardButton("❌ Bekor qilish", s"goBackToFolders_$page")))
//              ).some,
//            )
//            _ <- redisClient.put(
//              s"${user.id}+inputFolder",
//              message.messageId.toString + "_" + page,
//              1.day,
//            )
//          } yield ()
//
//        case regexActionWithPage("goBackToFolders", page) =>
//          showFolders(user.id, message.messageId, page.toInt)
//
//        case regexUUIDWithActionAndPage(folderId, "folder", page) =>
//          showTasks(
//            user.id,
//            message.messageId,
//            FolderId(UUID.fromString(folderId)),
//            1,
//            page.toInt,
//          )
//            .flatMap(_ => redisClient.del(s"${user.id}+inputTask"))
//
//        case regexActionWithInfo("pagination", action) =>
//          telegramClient.answerCallbackQuery(
//            callbackQueryId = callbackQueryId,
//            text = action,
//            showAlert = true,
//            cache_time = 3,
//          )
//
//        case regexActionsWithPage("pagination", "folder", page) =>
//          showFolders(user.id, message.messageId, page.toInt)
//
//        case regexActionAndUUIDWithDigits("pagination", folderId, page, folderPage) =>
//          showTasks(
//            user.id,
//            message.messageId,
//            FolderId(UUID.fromString(folderId)),
//            page.toInt,
//            folderPage.toInt,
//          )
//
//        case regexUUIDWithActionAndDigits(taskIdStr, "task", page, folderPage) =>
//          enterTask(
//            user.id,
//            TaskId(UUID.fromString(taskIdStr)),
//            message.messageId,
//            page.toInt,
//            folderPage.toInt,
//          )
//
//        case regexUUIDWithActionAndDigits(folderId, "goBackToTasks", page, folderPage) =>
//          showTasks(
//            user.id,
//            message.messageId,
//            FolderId(UUID.fromString(folderId)),
//            page.toInt,
//            folderPage.toInt,
//          )
//
//        case regexUUIDWithActionAndDigits(taskId, "play", page, folderPage) =>
//          playTask(
//            user.id,
//            TaskId(UUID.fromString(taskId)),
//            message.messageId,
//            page.toInt,
//            folderPage.toInt,
//          )
//
//        case regexUUIDWithActionAndDigits(taskId, "pause", page, folderPage) =>
//          pauseTask(
//            user.id,
//            TaskId(UUID.fromString(taskId)),
//            message.messageId,
//            page.toInt,
//            folderPage.toInt,
//          )
//        case regexUUIDWithActionAndDigits(taskId, "next", page, folderPage) =>
//          changeTaskStatus(
//            user.id,
//            TaskId(UUID.fromString(taskId)),
//            message.messageId,
//            page.toInt,
//            folderPage.toInt,
//          )
//
//        case regexUUIDWithActionAndDigits(taskId, "actions", page, folderPage) =>
//          telegramClient
//            .answerCallbackQuery(
//              callbackQueryId,
//              "Ishlab chiqish jarayonida",
//              showAlert = true,
//              3,
//            )
//            .flatMap(_ => logger.info(taskId + "_" + page + "_" + folderPage))
//
//        case regexUUIDWithActionAndDigitsAndPage(folderId, "createTask", msgId, page, folderPage) =>
//          for {
//            _ <- telegramClient.editMessageText(
//              user.id,
//              message.messageId,
//              "✍\uFE0F Iltimos vazifa nomini kiriting:",
//            )
//            _ <- telegramClient.editMessageReplyMarkup(
//              user.id,
//              message.messageId,
//              ReplyInlineKeyboardMarkup(
//                List(List(InlineKeyboardButton("❌ Bekor qilish", folderId + s"_folder_$page")))
//              ).some,
//            )
//            _ <- redisClient.put(
//              s"${user.id}+inputTask",
//              folderId + "_createTask_" + msgId + "_" + page + "_" + folderPage,
//              1.day,
//            )
//          } yield ()
//
//        case _ => logger.warn("unknown data type")
//      }
//
//    private def setButtons(chatId: Long): F[Unit] =
//      telegramClient.sendMessage(
//        chatId,
//        "Xayrli kun! /folders orqali jildlar yaratishingiz va ularda vazifalarni ularga sarflangan vaqtlar bo'yicha saqlashingiz mumkin!\n\nQuyidagi tugmalar yordamida esa tanlangan statusdagi vazifalarni ko'rashingiz mumkin!",
//        ReplyKeyboardMarkup(
//          List(
//            List(KeyboardButton("TO DO \uD83D\uDCDD")),
//            List(KeyboardButton("IN PROGRESS ⏳")),
//            List(KeyboardButton("IN REVIEW \uD83D\uDC41")),
//            List(KeyboardButton("TESTING ⛓\uFE0F\u200D\uD83D\uDCA5"), KeyboardButton("DONE ✅")),
//            List(KeyboardButton("RESULTS \uD83D\uDC41")),
//          )
//        ).some,
//      )
//
//    private def sendFolders(chatId: Long): F[Unit] = for {
//      folders <- liteTasksRepository.getAllFolders(chatId, 5, 1)
//      buttons = folders.data.map { folder =>
//        List(
//          InlineKeyboardButton(
//            "\uD83D\uDCC2 " + folder.name.value,
//            folder.id.toString + "_folder_1",
//          )
//        )
//      }
//      total = folders.total / limit + 1
//      previousPage = "pagination_Sahifa mavjud emas!"
//      currentPage = s"pagination_Siz $total dan 1-sahifadasiz"
//      nextPage = if (2 <= total) s"pagination_folder_2" else "pagination_Sahifa mavjud emas!"
//      _ <- telegramClient.sendMessage(
//        chatId = chatId,
//        text = "\uD83D\uDDC2 Quyidagi jildlardan birini tanlang:",
//        replyMarkup = ReplyInlineKeyboardMarkup(
//          buttons :+ List(
//            InlineKeyboardButton("⬅\uFE0F", previousPage),
//            InlineKeyboardButton("1⃣", currentPage),
//            InlineKeyboardButton("➡\uFE0F", nextPage),
//          ) :+ List(InlineKeyboardButton("➕", "createFolder_1"))
//        ).some,
//      )
//    } yield ()
//
//    private def showFolders(
//        chatId: Long,
//        messageId: Long,
//        page: Int,
//      ): F[Unit] = for {
//      _ <- redisClient.del(chatId.toString + "+inputFolder")
//      folders <- liteTasksRepository.getAllFolders(chatId, limit, page)
//      buttons = folders.data.map { folder =>
//        List(
//          InlineKeyboardButton(
//            "\uD83D\uDCC2 " + folder.name.value,
//            folder.id.toString + "_folder_" + page,
//          )
//        )
//      }
//      _ <- telegramClient.editMessageText(
//        chatId,
//        messageId,
//        "\uD83D\uDDC2 Quyidagi jildlardan birini tanlang:",
//      )
//      total = folders.total / limit + 1
//      previousPage =
//        if (page - 1 > 0) s"pagination_folder_${page - 1}" else "pagination_Sahifa mavjud emas!"
//      currentPage = s"pagination_Siz $total dan $page-sahifadasiz"
//      nextPage =
//        if (page + 1 <= total) s"pagination_folder_${page + 1}"
//        else "pagination_Sahifa mavjud emas!"
//      _ <- telegramClient.editMessageReplyMarkup(
//        chatId = chatId,
//        messageId = messageId,
//        replyMarkup = ReplyInlineKeyboardMarkup(
//          buttons :+ List(
//            InlineKeyboardButton("⬅\uFE0F", previousPage),
//            InlineKeyboardButton(page.toString.map(c => s"$c⃣").mkString(""), currentPage),
//            InlineKeyboardButton("➡\uFE0F", nextPage),
//          ) :+ List(InlineKeyboardButton("➕", "createFolder_" + page))
//        ).some,
//      )
//
//    } yield ()
//
//    private def createFolder(
//        chatId: Long,
//        oldMessageId: Long,
//        newMessageId: Long,
//        folderName: NonEmptyString,
//        page: Int,
//      ): F[Unit] =
//      for {
//        _ <- telegramClient.deleteMessage(chatId = chatId, messageId = newMessageId)
//        id <- ID.make[F, FolderId]
//        now <- Calendar[F].currentZonedDateTime
//        _ <- liteTasksRepository.createFolder(
//          Folder(
//            id = id,
//            createdAt = now,
//            userId = chatId,
//            name = folderName,
//          )
//        )
//        _ <- showFolders(chatId, oldMessageId, page)
//      } yield ()
//
//    private def showTasks(
//        chatId: Long,
//        messageId: Long,
//        folderId: FolderId,
//        page: Int,
//        folderPage: Int,
//      ): F[Unit] =
//      for {
//        tasks <- liteTasksRepository.getAll(folderId, limit, page)
//        buttons = tasks.data.map { task =>
//          val emoji = task.status match {
//            case TaskStatus.ToDo => "\uD83D\uDCDD "
//            case TaskStatus.InProgress => "⏳ "
//            case TaskStatus.InReview => "\uD83D\uDC41 "
//            case TaskStatus.Testing => "⛓\uFE0F\u200D\uD83D\uDCA5 "
//            case TaskStatus.Done => "✅ "
//            case _ => ""
//          }
//          List(
//            InlineKeyboardButton(
//              emoji + task.name.value,
//              task.id.toString + s"_task_${page}_$folderPage",
//            )
//          )
//        }
//        _ <- telegramClient.editMessageText(
//          chatId,
//          messageId,
//          "\uD83C\uDFAF Quyidagi vazifalardan birini tanlang:",
//        )
//        total = tasks.total / limit + 1
//        previousPage =
//          if (page - 1 > 0) s"pagination_${folderId.toString}_${page - 1}_$folderPage"
//          else "pagination_Sahifa mavjud emas!"
//        currentPage = s"pagination_Siz $total dan $page-sahifadasiz"
//        nextPage =
//          if (page + 1 <= total) s"pagination_${folderId.toString}_${page + 1}_$folderPage"
//          else "pagination_Sahifani mavjud emas!"
//        _ <- telegramClient.editMessageReplyMarkup(
//          chatId,
//          messageId,
//          ReplyInlineKeyboardMarkup(
//            buttons :+
//              List(
//                InlineKeyboardButton("⬅\uFE0F", previousPage),
//                InlineKeyboardButton(page.toString.map(c => s"$c⃣").mkString(""), currentPage),
//                InlineKeyboardButton("➡\uFE0F", nextPage),
//              ) :+
//              List(
//                InlineKeyboardButton("↩\uFE0F", s"goBackToFolders_$folderPage"),
//                InlineKeyboardButton(
//                  "➕",
//                  folderId.toString + "_createTask_" + messageId + "_" + page + "_" + folderPage,
//                ),
//              )
//          ).some,
//        )
//      } yield ()
//
//    def createTask(
//        chatId: Long,
//        oldMessageId: Long,
//        newMessageId: Long,
//        folderId: FolderId,
//        taskName: NonEmptyString,
//        page: Int,
//        folderPage: Int,
//      ): F[Unit] =
//      for {
//        _ <- telegramClient.deleteMessage(chatId = chatId, messageId = newMessageId)
//        id <- ID.make[F, TaskId]
//        now <- Calendar[F].currentZonedDateTime
//        _ <- liteTasksRepository.create(
//          LiteTask(
//            id = id,
//            createdAt = now,
//            userId = chatId,
//            folderId = folderId,
//            name = taskName,
//            status = TaskStatus.ToDo,
//            startedAt = None,
//            finishedAt = None,
//            duration = 0,
//          )
//        )
//        _ <- redisClient.del(chatId.toString + "+inputTask")
//        _ <- showTasks(chatId, oldMessageId, folderId, page, folderPage)
//      } yield ()
//
//    private def enterTask(
//        chatId: Long,
//        taskId: TaskId,
//        messageId: Long,
//        page: Int,
//        folderPage: Int,
//      ): F[Unit] =
//      liteTasksRepository.findById(taskId).flatMap { taskOpt =>
//        taskOpt.fold(
//          telegramClient.sendMessage(chatId, "❌ Vazifa haqida ma'lumot topilmadi!")
//        ) { task =>
//          val tuple =
//            if (task.startedAt.nonEmpty && task.finishedAt.isEmpty)
//              (
//                InlineKeyboardButton(
//                  "⏸\uFE0F",
//                  task.id.toString + s"_pause_${page}_$folderPage",
//                ),
//                task.name.value + s"\n\nTask begin: ${task
//                    .startedAt
//                    .map { zd =>
//                      val d = zd.plusHours(5)
//                      s"${d.toLocalDate} - ${d.getHour}:${d.getMinute}"
//                    }
//                    .getOrElse("")}",
//              )
//            else
//              (
//                InlineKeyboardButton(
//                  "▶\uFE0F",
//                  task.id.toString + s"_play_${page}_$folderPage",
//                ),
//                task.name.value + s"\n\nTask duration: ${task.duration} minutes",
//              )
//
//          val buttons = ReplyInlineKeyboardMarkup(
//            List(
//              List(
//                tuple._1,
//                InlineKeyboardButton("⏩", task.id.toString + s"_next_${page}_$folderPage"),
//                InlineKeyboardButton("*⃣", task.id.toString + s"_actions_${page}_$folderPage"),
//              ),
//              List(
//                InlineKeyboardButton(
//                  "↩\uFE0F",
//                  task.folderId.toString + s"_goBackToTasks_${page}_$folderPage",
//                )
//              ),
//            )
//          )
//          val emoji = task.status match {
//            case TaskStatus.ToDo => "\uD83D\uDCDD "
//            case TaskStatus.InProgress => "⏳ "
//            case TaskStatus.InReview => "\uD83D\uDC41 "
//            case TaskStatus.Testing => "⛓\uFE0F\u200D\uD83D\uDCA5 "
//            case TaskStatus.Done => "✅ "
//            case _ => ""
//          }
//          telegramClient
//            .editMessageText(chatId, messageId, emoji + tuple._2)
//            .flatMap(_ => telegramClient.editMessageReplyMarkup(chatId, messageId, buttons.some))
//        }
//      }
//
//    private def playTask(
//        chatId: Long,
//        taskId: TaskId,
//        messageId: Long,
//        page: Int,
//        folderPage: Int,
//      ): F[Unit] =
//      liteTasksRepository
//        .findById(taskId)
//        .flatMap(taskOpt =>
//          taskOpt.fold(telegramClient.sendMessage(chatId, "Vazifa ma'lumotlari topilmadi!")) {
//            task =>
//              for {
//                now <- Calendar[F].currentZonedDateTime
//                _ <- liteTasksRepository.update(
//                  LiteTask(
//                    id = task.id,
//                    createdAt = task.createdAt,
//                    userId = task.userId,
//                    folderId = task.folderId,
//                    name = task.name,
//                    status =
//                      if (task.status == TaskStatus.ToDo) TaskStatus.InProgress else task.status,
//                    startedAt = now.some,
//                    finishedAt = None,
//                    duration = task.duration,
//                  )
//                )
//                _ <- enterTask(chatId, taskId, messageId, page, folderPage)
//              } yield ()
//          }
//        )
//
//    private def pauseTask(
//        chatId: Long,
//        taskId: TaskId,
//        messageId: Long,
//        page: Int,
//        folderPage: Int,
//      ): F[Unit] =
//      liteTasksRepository
//        .findById(taskId)
//        .flatMap(taskOpt =>
//          taskOpt.fold(telegramClient.sendMessage(chatId, "Vazifa ma'lumotlari topilmadi!")) {
//            task =>
//              for {
//                now <- Calendar[F].currentZonedDateTime
//                additionalMinutes = Duration
//                  .between(task.startedAt.getOrElse(now), now)
//                  .toMinutes
//                _ <- liteTasksRepository.update(
//                  LiteTask(
//                    id = task.id,
//                    createdAt = task.createdAt,
//                    userId = task.userId,
//                    folderId = task.folderId,
//                    name = task.name,
//                    status = task.status,
//                    startedAt = task.startedAt,
//                    finishedAt = now.some,
//                    duration = task.duration + additionalMinutes,
//                  )
//                )
//                _ <- enterTask(chatId, taskId, messageId, page, folderPage)
//              } yield ()
//          }
//        )
//
//    private def changeTaskStatus(
//        chatId: Long,
//        taskId: TaskId,
//        messageId: Long,
//        page: Int,
//        folderPage: Int,
//      ): F[Unit] =
//      liteTasksRepository
//        .findById(taskId)
//        .flatMap(taskOpt =>
//          taskOpt.fold(telegramClient.sendMessage(chatId, "Vazifa ma'lumotlari topilmadi!")) {
//            task =>
//              for {
//                now <- Calendar[F].currentZonedDateTime
//                additionalMinutes = Duration
//                  .between(task.startedAt.getOrElse(now), now)
//                  .toMinutes
//                status = task.status match {
//                  case TaskStatus.ToDo => TaskStatus.InProgress
//                  case TaskStatus.InProgress => TaskStatus.InReview
//                  case TaskStatus.InReview => TaskStatus.Testing
//                  case TaskStatus.Testing => TaskStatus.Done
//                  case _ => TaskStatus.ToDo
//                }
//                _ <- liteTasksRepository.update(
//                  LiteTask(
//                    id = task.id,
//                    createdAt = task.createdAt,
//                    userId = task.userId,
//                    folderId = task.folderId,
//                    name = task.name,
//                    status = status,
//                    startedAt = None,
//                    finishedAt = now.some,
//                    duration = task.duration + additionalMinutes,
//                  )
//                )
//                _ <- enterTask(chatId, taskId, messageId, page, folderPage)
//              } yield ()
//          }
//        )
//  }
//}
