package tm.services

import java.time.ZonedDateTime

import cats.MonadThrow
import cats.data.OptionT
import cats.implicits._

import tm.domain.PersonId
import tm.domain.notifications._
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.exception.AError
import tm.repositories.NotificationsRepository
import tm.repositories.UsersRepository
import tm.utils.ID

trait NotificationService[F[_]] {
  // Core notification operations
  def createNotification(request: CreateNotificationRequest): F[Notification]
  def sendNotification(notification: Notification): F[Unit]
  def getUserNotifications(
      userId: PersonId,
      filters: NotificationFilters,
    ): F[(List[Notification], Long)]
  def getUnreadNotifications(userId: PersonId): F[List[Notification]]
  def getUnreadCount(userId: PersonId): F[Long]
  def markAsRead(notificationId: NotificationId, userId: PersonId): F[Unit]
  def markAllAsRead(userId: PersonId): F[Unit]
  def deleteNotification(notificationId: NotificationId, userId: PersonId): F[Unit]

  // Notification settings
  def getNotificationSettings(userId: PersonId): F[NotificationSettings]
  def updateNotificationSettings(
      userId: PersonId,
      request: UpdateNotificationSettingsRequest,
    ): F[NotificationSettings]

  // Bulk operations
  def sendBulkNotification(request: BulkNotificationRequest): F[List[Notification]]
  def processScheduledNotifications(): F[Int]

  // Analytics and statistics
  def getNotificationStats(userId: PersonId): F[NotificationStats]
  def searchNotifications(userId: PersonId, query: String): F[List[Notification]]

  // Template-based notifications
  def sendTemplatedNotification(
      userId: PersonId,
      notificationType: NotificationType,
      variables: Map[String, String],
      deliveryMethods: Set[DeliveryMethod] = Set(DeliveryMethod.InApp),
    ): F[Notification]

  // System notifications
  def sendSystemNotification(
      userIds: List[PersonId],
      title: String,
      content: String,
      priority: NotificationPriority = NotificationPriority.Normal,
    ): F[List[Notification]]

  // Delivery management
  def retryFailedDeliveries(): F[Int]
  def cleanupExpiredNotifications(): F[Int]

  // User preference checks
  def shouldSendNotification(
      userId: PersonId,
      notificationType: NotificationType,
      deliveryMethod: DeliveryMethod,
    ): F[Boolean]
  def isUserInQuietHours(userId: PersonId): F[Boolean]
}

object NotificationService {
  def make[F[_]: MonadThrow: Calendar: GenUUID](
      notificationsRepo: NotificationsRepository[F],
      usersRepo: UsersRepository[F],
      deliveryProvider: NotificationDeliveryProvider[F],
    ): NotificationService[F] = new NotificationService[F] {
    override def createNotification(request: CreateNotificationRequest): F[Notification] =
      for {
        notificationId <- ID.make[F, NotificationId]
        now <- Calendar[F].currentZonedDateTime
        notification = Notification(
          id = notificationId,
          userId = request.userId,
          title = request.title,
          content = request.content,
          notificationType = request.notificationType,
          relatedEntityId = request.relatedEntityId,
          relatedEntityType = request.relatedEntityType,
          isRead = false,
          priority = request.priority,
          deliveryMethods = request.deliveryMethods,
          metadata = request.metadata,
          scheduledAt = request.scheduledAt,
          sentAt = None,
          readAt = None,
          expiresAt = None, // TODO: Calculate based on type
          actionUrl = request.actionUrl,
          actionLabel = request.actionLabel,
          createdAt = now,
          updatedAt = now,
        )
        _ <- notificationsRepo.createNotification(notification)
      } yield notification

    override def sendNotification(notification: Notification): F[Unit] =
      for {
        // Check user preferences for each delivery method
        allowedMethods <- notification.deliveryMethods.toList.filterA { method =>
          shouldSendNotification(notification.userId, notification.notificationType, method)
        }

        // Check quiet hours
        inQuietHours <- isUserInQuietHours(notification.userId)

        // Filter out real-time methods during quiet hours
        finalMethods =
          if (inQuietHours)
            allowedMethods.filterNot(method =>
              method == DeliveryMethod.Push || method == DeliveryMethod.SMS
            )
          else
            allowedMethods

        // Send through each allowed delivery method
        _ <- finalMethods.traverse_ { method =>
          deliveryProvider.sendNotification(notification, method).handleError { error =>
            // Log delivery failure but don't fail the entire operation
            createDeliveryLog(
              notification.id,
              method,
              DeliveryStatus.Failed,
              Some(error.getMessage),
            )
          }
        }

        // Mark notification as sent
        _ <- notificationsRepo.markAsSent(notification.id)
      } yield ()

    override def getUserNotifications(
        userId: PersonId,
        filters: NotificationFilters,
      ): F[(List[Notification], Long)] =
      notificationsRepo.getUserNotifications(userId, filters)

    override def getUnreadNotifications(userId: PersonId): F[List[Notification]] =
      getUserNotifications(userId, NotificationFilters(isRead = Some(false), limit = Some(50)))
        .map(_._1)

    override def getUnreadCount(userId: PersonId): F[Long] =
      notificationsRepo.getUnreadCount(userId)

    override def markAsRead(notificationId: NotificationId, userId: PersonId): F[Unit] =
      notificationsRepo.markAsRead(notificationId, userId)

    override def markAllAsRead(userId: PersonId): F[Unit] =
      notificationsRepo.markAllAsRead(userId)

    override def deleteNotification(notificationId: NotificationId, userId: PersonId): F[Unit] =
      notificationsRepo.deleteNotification(notificationId, userId)

    override def getNotificationSettings(userId: PersonId): F[NotificationSettings] =
      notificationsRepo.getNotificationSettings(userId).flatMap {
        case Some(settings) => settings.pure[F]
        case None => createDefaultSettings(userId)
      }

    override def updateNotificationSettings(
        userId: PersonId,
        request: UpdateNotificationSettingsRequest,
      ): F[NotificationSettings] =
      for {
        currentSettings <- getNotificationSettings(userId)
        now <- Calendar[F].currentZonedDateTime
        updatedSettings = currentSettings.copy(
          emailNotifications =
            request.emailNotifications.getOrElse(currentSettings.emailNotifications),
          pushNotifications =
            request.pushNotifications.getOrElse(currentSettings.pushNotifications),
          smsNotifications = request.smsNotifications.getOrElse(currentSettings.smsNotifications),
          telegramNotifications =
            request.telegramNotifications.getOrElse(currentSettings.telegramNotifications),
          taskAssignments = request.taskAssignments.getOrElse(currentSettings.taskAssignments),
          taskReminders = request.taskReminders.getOrElse(currentSettings.taskReminders),
          projectUpdates = request.projectUpdates.getOrElse(currentSettings.projectUpdates),
          teamUpdates = request.teamUpdates.getOrElse(currentSettings.teamUpdates),
          dailyDigest = request.dailyDigest.getOrElse(currentSettings.dailyDigest),
          weeklyReport = request.weeklyReport.getOrElse(currentSettings.weeklyReport),
          productivityInsights =
            request.productivityInsights.getOrElse(currentSettings.productivityInsights),
          quietHours = request.quietHours.orElse(currentSettings.quietHours),
          timeZone = request.timeZone.getOrElse(currentSettings.timeZone),
          updatedAt = now,
        )
        _ <- notificationsRepo.upsertNotificationSettings(updatedSettings)
      } yield updatedSettings

    override def sendBulkNotification(request: BulkNotificationRequest): F[List[Notification]] =
      for {
        notifications <- request.userIds.traverse { userId =>
          createNotification(
            CreateNotificationRequest(
              userId = userId,
              title = request.title,
              content = request.content,
              notificationType = request.notificationType,
              priority = request.priority,
              deliveryMethods = request.deliveryMethods,
              scheduledAt = request.scheduledAt,
            )
          )
        }
        // Send immediately if not scheduled
        _ <-
          if (request.scheduledAt.isEmpty)
            notifications.traverse_(sendNotification)
          else
            ().pure[F]
      } yield notifications

    override def processScheduledNotifications(): F[Int] =
      for {
        now <- Calendar[F].currentZonedDateTime
        scheduledNotifications <- notificationsRepo.getScheduledNotifications(now)
        _ <- scheduledNotifications.traverse_(sendNotification)
      } yield scheduledNotifications.length

    override def getNotificationStats(userId: PersonId): F[NotificationStats] =
      notificationsRepo.getNotificationStats(userId)

    override def searchNotifications(userId: PersonId, query: String): F[List[Notification]] =
      notificationsRepo.searchNotifications(userId, query)

    override def sendTemplatedNotification(
        userId: PersonId,
        notificationType: NotificationType,
        variables: Map[String, String],
        deliveryMethods: Set[DeliveryMethod],
      ): F[Notification] =
      for {
        template <- notificationsRepo.getTemplateByType(notificationType.toString).flatMap {
          case Some(template) => template.pure[F]
          case None =>
            AError
              .BadRequest(s"Template not found for type: $notificationType")
              .raiseError[F, NotificationTemplate]
        }

        // Process template variables
        title = processTemplate(template.titleTemplate, variables)
        content = processTemplate(template.contentTemplate, variables)

        // Create and send notification
        notification <- createNotification(
          CreateNotificationRequest(
            userId = userId,
            title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom(title),
            content = content,
            notificationType = notificationType,
            priority = template.defaultPriority,
            deliveryMethods = deliveryMethods.intersect(template.supportedDeliveryMethods),
          )
        )

        _ <- sendNotification(notification)
      } yield notification

    override def sendSystemNotification(
        userIds: List[PersonId],
        title: String,
        content: String,
        priority: NotificationPriority,
      ): F[List[Notification]] =
      sendBulkNotification(
        BulkNotificationRequest(
          userIds = userIds,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom(title),
          content = content,
          notificationType = NotificationType.SystemAlert,
          priority = priority,
          deliveryMethods = Set(DeliveryMethod.InApp, DeliveryMethod.Email),
        )
      )

    override def retryFailedDeliveries(): F[Int] =
      for {
        failedDeliveries <- notificationsRepo.getFailedDeliveries(maxAttempts = 3)
        _ <- failedDeliveries.traverse_ { log =>
          for {
            notificationOpt <- notificationsRepo.findNotificationById(
              NotificationId(log.notificationId)
            )
            _ <- notificationOpt.traverse_ { notification =>
              deliveryProvider.sendNotification(notification, log.deliveryMethod).attempt.flatMap {
                case Right(_) =>
                  notificationsRepo.updateDeliveryLogStatus(
                    log.id,
                    DeliveryStatus.Delivered.toString,
                    log.attempts + 1,
                    None,
                    Some(ZonedDateTime.now()),
                  )
                case Left(error) =>
                  notificationsRepo.updateDeliveryLogStatus(
                    log.id,
                    DeliveryStatus.Failed.toString,
                    log.attempts + 1,
                    Some(error.getMessage),
                    None,
                  )
              }
            }
          } yield ()
        }
      } yield failedDeliveries.length

    override def cleanupExpiredNotifications(): F[Int] =
      for {
        _ <- notificationsRepo.cleanupExpiredNotifications()
        // Return count - this would need to be tracked in the repository method
      } yield 0 // Placeholder

    override def shouldSendNotification(
        userId: PersonId,
        notificationType: NotificationType,
        deliveryMethod: DeliveryMethod,
      ): F[Boolean] =
      for {
        settings <- getNotificationSettings(userId)
        typeAllowed = notificationType match {
          case NotificationType.TaskAssigned | NotificationType.TaskDue |
               NotificationType.TaskOverdue =>
            settings.taskAssignments
          case NotificationType.ProjectUpdate | NotificationType.ProjectDeadline =>
            settings.projectUpdates
          case NotificationType.TeamUpdate => settings.teamUpdates
          case NotificationType.TimeReminder | NotificationType.BreakReminder =>
            settings.taskReminders
          case NotificationType.ProductivityInsight => settings.productivityInsights
          case _ => true // System alerts and other types are always allowed
        }
        methodAllowed = deliveryMethod match {
          case DeliveryMethod.Email => settings.emailNotifications
          case DeliveryMethod.Push => settings.pushNotifications
          case DeliveryMethod.SMS => settings.smsNotifications
          case DeliveryMethod.Telegram => settings.telegramNotifications
          case DeliveryMethod.InApp | DeliveryMethod.WebSocket => true // Always allowed
        }
      } yield typeAllowed && methodAllowed

    override def isUserInQuietHours(userId: PersonId): F[Boolean] =
      for {
        now <- Calendar[F].currentZonedDateTime
        inQuietHours <- notificationsRepo.isUserInQuietHours(userId, now)
      } yield inQuietHours

    // Private helper methods

    private def createDefaultSettings(userId: PersonId): F[NotificationSettings] =
      for {
        settingsId <- ID.make[F, NotificationId]
        now <- Calendar[F].currentZonedDateTime
        defaultSettings = NotificationSettings(
          id = settingsId,
          userId = userId,
          emailNotifications = true,
          pushNotifications = true,
          smsNotifications = false,
          telegramNotifications = true,
          taskAssignments = true,
          taskReminders = true,
          projectUpdates = true,
          teamUpdates = true,
          dailyDigest = true,
          weeklyReport = false,
          productivityInsights = true,
          quietHours = None,
          timeZone = "UTC",
          createdAt = now,
          updatedAt = now,
        )
        _ <- notificationsRepo.upsertNotificationSettings(defaultSettings)
      } yield defaultSettings

    private def processTemplate(template: String, variables: Map[String, String]): String =
      variables.foldLeft(template) {
        case (text, (key, value)) =>
          text.replace(s"{{$key}}", value)
      }

    private def createDeliveryLog(
        notificationId: NotificationId,
        deliveryMethod: DeliveryMethod,
        status: DeliveryStatus,
        errorMessage: Option[String],
      ): F[Unit] =
      for {
        logId <- GenUUID[F].make
        now <- Calendar[F].currentZonedDateTime
        log = NotificationDeliveryLog(
          id = logId,
          notificationId = notificationId,
          deliveryMethod = deliveryMethod,
          status = status,
          attempts = 1,
          errorMessage = errorMessage,
          deliveredAt = if (status == DeliveryStatus.Delivered) Some(now) else None,
          createdAt = now,
        )
        _ <- notificationsRepo.createDeliveryLog(log)
      } yield ()
  }
}

// Notification Delivery Provider trait
trait NotificationDeliveryProvider[F[_]] {
  def sendNotification(notification: Notification, deliveryMethod: DeliveryMethod): F[Unit]
  def isHealthy(deliveryMethod: DeliveryMethod): F[Boolean]
  def getDeliveryStatus(
      notificationId: NotificationId,
      deliveryMethod: DeliveryMethod,
    ): F[Option[DeliveryStatus]]
}

// Mock implementation for now
object MockNotificationDeliveryProvider {
  def make[F[_]: MonadThrow]: NotificationDeliveryProvider[F] =
    new NotificationDeliveryProvider[F] {
      override def sendNotification(
          notification: Notification,
          deliveryMethod: DeliveryMethod,
        ): F[Unit] =
        deliveryMethod match {
          case DeliveryMethod.InApp | DeliveryMethod.WebSocket =>
            // These are handled by the websocket service
            ().pure[F]
          case DeliveryMethod.Email =>
            // TODO: Implement email sending
            ().pure[F]
          case DeliveryMethod.SMS =>
            // TODO: Implement SMS sending
            ().pure[F]
          case DeliveryMethod.Push =>
            // TODO: Implement push notification sending
            ().pure[F]
          case DeliveryMethod.Telegram =>
            // TODO: Implement Telegram sending
            ().pure[F]
        }

      override def isHealthy(deliveryMethod: DeliveryMethod): F[Boolean] =
        true.pure[F] // Mock implementation

      override def getDeliveryStatus(
          notificationId: NotificationId,
          deliveryMethod: DeliveryMethod,
        ): F[Option[DeliveryStatus]] =
        Some(DeliveryStatus.Delivered).pure[F] // Mock implementation
    }
}
