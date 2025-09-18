package tm.endpoint.routes

import cats.effect.Async
import cats.implicits._
import io.estatico.newtype.ops.toCoercibleIdOps
import org.http4s._
import org.http4s.circe.CirceEntityCodec._

import tm.domain.auth.AuthedUser
import tm.domain.notifications._
import tm.endpoint.routes.utils.QueryParam._
import tm.services.NotificationService
import tm.support.http4s.utils.Routes

final case class NotificationRoutes[F[_]: Async](
    notificationService: NotificationService[F]
  ) extends Routes[F, AuthedUser] {
  override val path: String = "/notifications"
  override val public: HttpRoutes[F] = HttpRoutes.empty[F]
  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {

    // Get user notifications with filters
    case req @ GET -> Root :? OptionalIsRead(isRead) +& OptionalNotificationType(
           notificationType
         ) +& OptionalPriority(priority) +& OptionalLimit(limit) +& OptionalOffset(
           offset
         ) as user =>
      val filters = NotificationFilters(
        isRead = isRead,
        notificationType = notificationType.map(parseNotificationType),
        priority = priority.map(parseNotificationPriority),
        limit = limit,
        offset = offset,
      )
      for {
        (notifications, total) <- notificationService.getUserNotifications(user.id, filters)
        response <- Ok(
          Map(
            "notifications" -> notifications,
            "total" -> total,
            "limit" -> filters.limit.getOrElse(20),
            "offset" -> filters.offset.getOrElse(0),
          )
        )
      } yield response

    // Get unread notifications
    case GET -> Root / "unread" as user =>
      for {
        notifications <- notificationService.getUnreadNotifications(user.id)
        response <- Ok(notifications)
      } yield response

    // Get unread count
    case GET -> Root / "unread-count" as user =>
      for {
        count <- notificationService.getUnreadCount(user.id)
        response <- Ok(Map("unreadCount" -> count))
      } yield response

    // Mark notification as read
    case POST -> Root / UUIDVar(notificationId) / "read" as user =>
      for {
        _ <- notificationService.markAsRead(notificationId.coerce[NotificationId], user.id)
        response <- Ok(Map("success" -> true))
      } yield response

    // Mark all notifications as read
    case POST -> Root / "mark-all-read" as user =>
      for {
        _ <- notificationService.markAllAsRead(user.id)
        response <- Ok(Map("success" -> true))
      } yield response

    // Delete notification
    case DELETE -> Root / UUIDVar(notificationId) as user =>
      for {
        _ <- notificationService.deleteNotification(notificationId.coerce[NotificationId], user.id)
        response <- Ok(Map("success" -> true))
      } yield response

    // Create notification (admin/system use)
    case req @ POST -> Root as user =>
      for {
        createRequest <- req.req.as[CreateNotificationRequest]
        notification <- notificationService.createNotification(createRequest)
        _ <- notificationService.sendNotification(notification)
        response <- Created(notification)
      } yield response

    // Send bulk notification (admin use)
    case req @ POST -> Root / "bulk" as user =>
      for {
        bulkRequest <- req.req.as[BulkNotificationRequest]
        notifications <- notificationService.sendBulkNotification(bulkRequest)
        response <- Ok(
          Map(
            "sent" -> notifications.length,
            "notifications" -> notifications,
          )
        )
      } yield response

    // Send templated notification
    case req @ POST -> Root / "templated" as user =>
      for {
        request <- req.req.as[TemplatedNotificationRequest]
        notification <- notificationService.sendTemplatedNotification(
          request.userId,
          request.notificationType,
          request.variables,
          request.deliveryMethods,
        )
        response <- Created(notification)
      } yield response

    // Get notification settings
    case GET -> Root / "settings" as user =>
      for {
        settings <- notificationService.getNotificationSettings(user.id)
        response <- Ok(settings)
      } yield response

    // Update notification settings
    case req @ PUT -> Root / "settings" as user =>
      for {
        updateRequest <- req.req.as[UpdateNotificationSettingsRequest]
        settings <- notificationService.updateNotificationSettings(user.id, updateRequest)
        response <- Ok(settings)
      } yield response

    // Get notification statistics
    case GET -> Root / "stats" as user =>
      for {
        stats <- notificationService.getNotificationStats(user.id)
        response <- Ok(stats)
      } yield response

    // Search notifications
    case GET -> Root / "search" :? QueryParam(query) as user =>
      for {
        results <- notificationService.searchNotifications(user.id, query)
        response <- Ok(
          Map(
            "query" -> query,
            "results" -> results,
            "count" -> results.length,
          )
        )
      } yield response

    // Test notification (development/admin)
    case req @ POST -> Root / "test" as user =>
      for {
        testRequest <- req.req.as[TestNotificationRequest]
        notification <- notificationService.createNotification(
          CreateNotificationRequest(
            userId = user.id,
            title = testRequest.title,
            content = testRequest.content,
            notificationType = NotificationType.SystemAlert,
            priority = testRequest.priority.getOrElse(NotificationPriority.Normal),
            deliveryMethods = testRequest.deliveryMethods.getOrElse(Set(DeliveryMethod.InApp)),
          )
        )
        _ <- notificationService.sendNotification(notification)
        response <- Ok(
          Map(
            "message" -> "Test notification sent",
            "notification" -> notification,
          )
        )
      } yield response

    // Get notification preferences summary
    case GET -> Root / "preferences" as user =>
      for {
        settings <- notificationService.getNotificationSettings(user.id)
        inQuietHours <- notificationService.isUserInQuietHours(user.id)
        response <- Ok(
          Map(
            "emailEnabled" -> settings.emailNotifications,
            "pushEnabled" -> settings.pushNotifications,
            "smsEnabled" -> settings.smsNotifications,
            "telegramEnabled" -> settings.telegramNotifications,
            "taskReminders" -> settings.taskReminders,
            "projectUpdates" -> settings.projectUpdates,
            "teamUpdates" -> settings.teamUpdates,
            "quietHours" -> settings.quietHours,
            "inQuietHours" -> inQuietHours,
            "timeZone" -> settings.timeZone,
          )
        )
      } yield response

    // Enable/disable specific notification type
    case req @ POST -> Root / "preferences" / notificationTypeStr / "toggle" as user =>
      val notificationType = parseNotificationType(notificationTypeStr)
      for {
        toggleRequest <- req.req.as[NotificationToggleRequest]
        currentSettings <- notificationService.getNotificationSettings(user.id)

        updateRequest = notificationType match {
          case NotificationType.TaskAssigned | NotificationType.TaskDue =>
            UpdateNotificationSettingsRequest(taskAssignments = Some(toggleRequest.enabled))
          case NotificationType.ProjectUpdate =>
            UpdateNotificationSettingsRequest(projectUpdates = Some(toggleRequest.enabled))
          case NotificationType.TeamUpdate =>
            UpdateNotificationSettingsRequest(teamUpdates = Some(toggleRequest.enabled))
          case NotificationType.TimeReminder =>
            UpdateNotificationSettingsRequest(taskReminders = Some(toggleRequest.enabled))
          case NotificationType.ProductivityInsight =>
            UpdateNotificationSettingsRequest(productivityInsights = Some(toggleRequest.enabled))
          case _ =>
            UpdateNotificationSettingsRequest() // No change for other types
        }

        updatedSettings <- notificationService.updateNotificationSettings(user.id, updateRequest)
        response <- Ok(
          Map(
            "notificationType" -> notificationTypeStr,
            "enabled" -> toggleRequest.enabled,
            "updated" -> true,
          )
        )
      } yield response

    // Get delivery methods status
    case GET -> Root / "delivery-methods" as user =>
      for {
        settings <- notificationService.getNotificationSettings(user.id)
        response <- Ok(
          Map(
            "email" -> Map("enabled" -> settings.emailNotifications, "available" -> true),
            "push" -> Map("enabled" -> settings.pushNotifications, "available" -> true),
            "sms" -> Map("enabled" -> settings.smsNotifications, "available" -> true),
            "telegram" -> Map("enabled" -> settings.telegramNotifications, "available" -> true),
            "inApp" -> Map("enabled" -> true, "available" -> true),
            "webSocket" -> Map("enabled" -> true, "available" -> true),
          )
        )
      } yield response

    // Admin routes (would need proper admin authorization)
    case POST -> Root / "admin" / "process-scheduled" as user =>
      // TODO: Add admin authorization check
      for {
        processed <- notificationService.processScheduledNotifications()
        response <- Ok(
          Map(
            "processedCount" -> processed,
            "message" -> s"Processed $processed scheduled notifications",
          )
        )
      } yield response

    case POST -> Root / "admin" / "retry-failed" as user =>
      // TODO: Add admin authorization check
      for {
        retried <- notificationService.retryFailedDeliveries()
        response <- Ok(
          Map(
            "retriedCount" -> retried,
            "message" -> s"Retried $retried failed deliveries",
          )
        )
      } yield response

    case POST -> Root / "admin" / "cleanup" as user =>
      // TODO: Add admin authorization check
      for {
        cleaned <- notificationService.cleanupExpiredNotifications()
        response <- Ok(
          Map(
            "cleanedCount" -> cleaned,
            "message" -> s"Cleaned up $cleaned expired notifications",
          )
        )
      } yield response
  }

  // Helper methods for parsing enum values
  private def parseNotificationType(str: String): NotificationType =
    str match {
      case "TaskAssigned" => NotificationType.TaskAssigned
      case "TaskDue" => NotificationType.TaskDue
      case "TaskCompleted" => NotificationType.TaskCompleted
      case "TaskOverdue" => NotificationType.TaskOverdue
      case "ProjectUpdate" => NotificationType.ProjectUpdate
      case "ProjectDeadline" => NotificationType.ProjectDeadline
      case "TimeReminder" => NotificationType.TimeReminder
      case "BreakReminder" => NotificationType.BreakReminder
      case "DailyGoalReached" => NotificationType.DailyGoalReached
      case "WeeklyGoalReached" => NotificationType.WeeklyGoalReached
      case "SystemAlert" => NotificationType.SystemAlert
      case "TeamUpdate" => NotificationType.TeamUpdate
      case "MentionInComment" => NotificationType.MentionInComment
      case "WorkSessionStarted" => NotificationType.WorkSessionStarted
      case "ProductivityInsight" => NotificationType.ProductivityInsight
      case _ => NotificationType.SystemAlert // Default fallback
    }

  private def parseNotificationPriority(str: String): NotificationPriority =
    str match {
      case "Low" => NotificationPriority.Low
      case "Normal" => NotificationPriority.Normal
      case "High" => NotificationPriority.High
      case "Critical" => NotificationPriority.Critical
      case _ => NotificationPriority.Normal // Default fallback
    }
}

// Additional request DTOs
case class TemplatedNotificationRequest(
    userId: tm.domain.PersonId,
    notificationType: NotificationType,
    variables: Map[String, String],
    deliveryMethods: Set[DeliveryMethod] = Set(DeliveryMethod.InApp),
  )

case class TestNotificationRequest(
    title: eu.timepit.refined.types.string.NonEmptyString,
    content: String,
    priority: Option[NotificationPriority] = None,
    deliveryMethods: Option[Set[DeliveryMethod]] = None,
  )

case class NotificationToggleRequest(
    enabled: Boolean
  )

// Codecs for additional DTOs
object TemplatedNotificationRequest {
  implicit val codec: io.circe.Codec[TemplatedNotificationRequest] =
    io.circe.generic.semiauto.deriveCodec
}

object TestNotificationRequest {
  implicit val codec: io.circe.Codec[TestNotificationRequest] =
    io.circe.generic.semiauto.deriveCodec
}

object NotificationToggleRequest {
  implicit val codec: io.circe.Codec[NotificationToggleRequest] =
    io.circe.generic.semiauto.deriveCodec
}

object NotificationRoutes {
  def apply[F[_]: Async](notificationService: NotificationService[F]): NotificationRoutes[F] =
    new NotificationRoutes[F](notificationService)
}
