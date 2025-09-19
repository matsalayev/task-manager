package tm.endpoint.routes

import cats.effect.Async
import cats.implicits._
import io.circe.Json
import io.circe.syntax._
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
          Json.obj(
            "notifications" -> notifications.asJson,
            "total" -> Json.fromLong(total),
            "limit" -> Json.fromInt(filters.limit.getOrElse(20)),
            "offset" -> Json.fromInt(filters.offset.getOrElse(0)),
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
        response <- Ok(Json.obj("unreadCount" -> Json.fromLong(count)))
      } yield response

    // Mark notification as read
    case POST -> Root / UUIDVar(notificationId) / "read" as user =>
      for {
        _ <- notificationService.markAsRead(notificationId.coerce[NotificationId], user.id)
        response <- Ok(Json.obj("success" -> Json.fromBoolean(true)))
      } yield response

    // Mark all notifications as read
    case POST -> Root / "mark-all-read" as user =>
      for {
        _ <- notificationService.markAllAsRead(user.id)
        response <- Ok(Json.obj("success" -> Json.fromBoolean(true)))
      } yield response

    // Delete notification
    case DELETE -> Root / UUIDVar(notificationId) as user =>
      for {
        _ <- notificationService.deleteNotification(notificationId.coerce[NotificationId], user.id)
        response <- Ok(Json.obj("success" -> Json.fromBoolean(true)))
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
          Json.obj(
            "sent" -> Json.fromInt(notifications.length),
            "notifications" -> notifications.asJson,
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
    case GET -> Root / "search" :? tm.endpoint.routes.utils.QueryParam.QueryParam(query) as user =>
      for {
        results <- notificationService.searchNotifications(user.id, query)
        response <- Ok(
          Json.obj(
            "query" -> Json.fromString(query),
            "results" -> results.asJson,
            "count" -> Json.fromInt(results.length),
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
          Json.obj(
            "message" -> Json.fromString("Test notification sent"),
            "notification" -> notification.asJson,
          )
        )
      } yield response

    // Get notification preferences summary
    case GET -> Root / "preferences" as user =>
      for {
        settings <- notificationService.getNotificationSettings(user.id)
        inQuietHours <- notificationService.isUserInQuietHours(user.id)
        response <- Ok(
          Json.obj(
            "emailEnabled" -> Json.fromBoolean(settings.emailNotifications),
            "pushEnabled" -> Json.fromBoolean(settings.pushNotifications),
            "smsEnabled" -> Json.fromBoolean(settings.smsNotifications),
            "telegramEnabled" -> Json.fromBoolean(settings.telegramNotifications),
            "taskReminders" -> Json.fromBoolean(settings.taskReminders),
            "projectUpdates" -> Json.fromBoolean(settings.projectUpdates),
            "teamUpdates" -> Json.fromBoolean(settings.teamUpdates),
            "quietHours" -> settings.quietHours.asJson,
            "inQuietHours" -> Json.fromBoolean(inQuietHours),
            "timeZone" -> Json.fromString(settings.timeZone),
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
          Json.obj(
            "notificationType" -> Json.fromString(notificationTypeStr),
            "enabled" -> Json.fromBoolean(toggleRequest.enabled),
            "updated" -> Json.fromBoolean(true),
          )
        )
      } yield response

    // Get delivery methods status
    case GET -> Root / "delivery-methods" as user =>
      for {
        settings <- notificationService.getNotificationSettings(user.id)
        response <- Ok(
          Json.obj(
            "email" -> Json.obj(
              "enabled" -> Json.fromBoolean(settings.emailNotifications),
              "available" -> Json.fromBoolean(true),
            ),
            "push" -> Json.obj(
              "enabled" -> Json.fromBoolean(settings.pushNotifications),
              "available" -> Json.fromBoolean(true),
            ),
            "sms" -> Json.obj(
              "enabled" -> Json.fromBoolean(settings.smsNotifications),
              "available" -> Json.fromBoolean(true),
            ),
            "telegram" -> Json.obj(
              "enabled" -> Json.fromBoolean(settings.telegramNotifications),
              "available" -> Json.fromBoolean(true),
            ),
            "inApp" -> Json
              .obj("enabled" -> Json.fromBoolean(true), "available" -> Json.fromBoolean(true)),
            "webSocket" -> Json.obj(
              "enabled" -> Json.fromBoolean(true),
              "available" -> Json.fromBoolean(true),
            ),
          )
        )
      } yield response

    // Admin routes (would need proper admin authorization)
    case POST -> Root / "admin" / "process-scheduled" as user =>
      // TODO: Add admin authorization check
      for {
        processed <- notificationService.processScheduledNotifications()
        response <- Ok(
          Json.obj(
            "processedCount" -> Json.fromInt(processed),
            "message" -> Json.fromString(s"Processed $processed scheduled notifications"),
          )
        )
      } yield response

    case POST -> Root / "admin" / "retry-failed" as user =>
      // TODO: Add admin authorization check
      for {
        retried <- notificationService.retryFailedDeliveries()
        response <- Ok(
          Json.obj(
            "retriedCount" -> Json.fromInt(retried),
            "message" -> Json.fromString(s"Retried $retried failed deliveries"),
          )
        )
      } yield response

    case POST -> Root / "admin" / "cleanup" as user =>
      // TODO: Add admin authorization check
      for {
        cleaned <- notificationService.cleanupExpiredNotifications()
        response <- Ok(
          Json.obj(
            "cleanedCount" -> Json.fromInt(cleaned),
            "message" -> Json.fromString(s"Cleaned up $cleaned expired notifications"),
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
  implicit val encoder: io.circe.Encoder[TemplatedNotificationRequest] =
    io.circe.generic.semiauto.deriveEncoder
  implicit val decoder: io.circe.Decoder[TemplatedNotificationRequest] =
    io.circe.generic.semiauto.deriveDecoder
}

object TestNotificationRequest {
  implicit val encoder: io.circe.Encoder[TestNotificationRequest] =
    io.circe.generic.semiauto.deriveEncoder
  implicit val decoder: io.circe.Decoder[TestNotificationRequest] =
    io.circe.generic.semiauto.deriveDecoder
}

object NotificationToggleRequest {
  implicit val codec: io.circe.Codec[NotificationToggleRequest] =
    io.circe.generic.semiauto.deriveCodec
}

object NotificationRoutes {
  def apply[F[_]: Async](notificationService: NotificationService[F]): NotificationRoutes[F] =
    new NotificationRoutes[F](notificationService)
}
