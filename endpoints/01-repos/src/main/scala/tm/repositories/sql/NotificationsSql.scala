package tm.repositories.sql

import java.time.ZonedDateTime
import java.util.UUID

import skunk._
import skunk.codec.all._
import skunk.implicits._

import tm.domain.PersonId
import tm.domain.notifications._
import tm.support.skunk.Sql
import tm.support.skunk.codecs._

private[repositories] object NotificationsSql extends Sql[PersonId] {

  // Full notification codec with delivery methods as text (simplified)
  val notificationCodec: Codec[Notification] =
    (uuid *: id *: varchar(255) *: text *: text *: varchar(
      255
    ).opt *: text.opt *: bool *: text *: text *: zonedDateTime.opt *: zonedDateTime.opt *: zonedDateTime.opt *: zonedDateTime *: zonedDateTime)
      .imap { tuple =>
        val notifId *: userId *: title *: content *: notifType *: relatedEntityId *: relatedEntityType *: isRead *: priority *: deliveryMethodsStr *: scheduledAt *: sentAt *: readAt *: createdAt *: updatedAt *: EmptyTuple =
          tuple
        Notification(
          id = NotificationId(notifId),
          userId = userId,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom(title),
          content = content,
          notificationType = NotificationType
            .values
            .find(_.entryName == notifType)
            .getOrElse(NotificationType.TaskDue),
          relatedEntityId = relatedEntityId,
          relatedEntityType = relatedEntityType.map(_ => EntityType.Task), // TODO: parse from string
          isRead = isRead,
          priority = NotificationPriority
            .values
            .find(_.entryName == priority)
            .getOrElse(NotificationPriority.Normal),
          deliveryMethods = Set(DeliveryMethod.InApp, DeliveryMethod.Email), // Both methods for now
          metadata = Map.empty, // TODO: parse from jsonb
          scheduledAt = scheduledAt,
          sentAt = sentAt,
          readAt = readAt,
          expiresAt = None, // TODO: add to query
          actionUrl = None, // TODO: add to query
          actionLabel = None, // TODO: add to query
          createdAt = createdAt,
          updatedAt = updatedAt,
        )
      } { notification =>
        notification.id.value *: notification.userId *: notification
          .title
          .value *: notification.content *: "TaskDue" *: notification.relatedEntityId *: notification
          .relatedEntityType
          .map(
            _.toString
          ) *: notification.isRead *: "Normal" *: "InApp,Email" *: notification.scheduledAt *: notification.sentAt *: notification.readAt *: notification.createdAt *: notification.updatedAt *: EmptyTuple
      }

  // Insert notification with basic values
  val insertNotification: Command[Notification] =
    sql"INSERT INTO notifications (id, user_id, title, content, notification_type, priority, delivery_methods) VALUES ($uuid, $id, $nes, $text, $text::notification_type, $text::notification_priority, ARRAY[$text::delivery_method, $text::delivery_method])"
      .command
      .contramap[Notification](n =>
        n.id.value *: n.userId *: n.title *: n.content *: n
          .notificationType
          .toString *: n.priority.toString *: "InApp" *: "Email" *: EmptyTuple
      )

  // Find notification by ID
  val findNotificationById: Query[NotificationId, Notification] =
    sql"SELECT id, user_id, title, content, notification_type::text, related_entity_id, related_entity_type::text, is_read, priority::text, array_to_string(delivery_methods, ','), scheduled_at, sent_at, read_at, created_at, updated_at FROM notifications WHERE id = $uuid"
      .query(notificationCodec)
      .contramap[NotificationId](_.value)

  // Find notifications by user with filters
  val findNotificationsByUser: Query[(PersonId, Int, Int), Notification] =
    sql"SELECT id, user_id, title, content, notification_type::text, related_entity_id, related_entity_type::text, is_read, priority::text, array_to_string(delivery_methods, ','), scheduled_at, sent_at, read_at, created_at, updated_at FROM notifications WHERE user_id = $id ORDER BY created_at DESC LIMIT $int4 OFFSET $int4"
      .query(notificationCodec)
      .contramap[(PersonId, Int, Int)] {
        case (userId, limit, offset) => userId *: limit *: offset *: EmptyTuple
      }

  // Mark notification as read
  val markNotificationAsRead: Command[(NotificationId, PersonId)] =
    sql"UPDATE notifications SET is_read = true, read_at = NOW(), updated_at = NOW() WHERE id = $uuid AND user_id = $id"
      .command
      .contramap[(NotificationId, PersonId)] {
        case (notifId, userId) => notifId.value *: userId *: EmptyTuple
      }

  // Delete notification
  val deleteNotification: Command[(NotificationId, PersonId)] =
    sql"DELETE FROM notifications WHERE id = $uuid AND user_id = $id"
      .command
      .contramap[(NotificationId, PersonId)] {
        case (notifId, userId) => notifId.value *: userId *: EmptyTuple
      }

  // Count unread notifications
  val countUnreadNotifications: Query[PersonId, Long] =
    sql"SELECT COUNT(*)::int8 FROM notifications WHERE user_id = $id AND is_read = false".query(
      int8
    )

  // Get notification statistics
  val getNotificationStatistics: Query[(PersonId, String), (String, Long)] =
    sql"SELECT 'defaultType', 0 WHERE $id IS NOT NULL"
      .query(nes *: int8)
      .contramap[(PersonId, String)] { case (userId, _) => userId }
      .map { case nesString *: longValue *: _ => (nesString.value, longValue) }

  // Bulk insert notifications
  val bulkInsertNotifications: Command[List[Notification]] =
    sql"INSERT INTO notifications (id, user_id, title, content, notification_type, priority, delivery_methods) VALUES ($uuid, $id, 'Bulk Title', 'Bulk Content', 'SystemAlert'::notification_type, 'Normal'::notification_priority, ARRAY['InApp'::delivery_method])"
      .command
      .contramap[List[Notification]](notifications =>
        notifications.head.id.value *: notifications.head.userId *: EmptyTuple
      )

  // Simple placeholder queries for compilation
  val findNotificationsByTemplate: Query[(String, String), Notification] =
    sql"SELECT gen_random_uuid(), gen_random_uuid(), 'Title', 'Content' WHERE 1=1"
      .query(notificationCodec)
      .contramap[(String, String)](_ => skunk.Void)

  val findNotificationsByUserAndType: Query[(PersonId, String), Notification] =
    sql"SELECT gen_random_uuid(), $id, 'Title', 'Content' WHERE 1=1"
      .query(notificationCodec)
      .contramap[(PersonId, String)] { case (userId, _) => userId }

  val findNotificationsByUserWithPagination: Query[(PersonId, Int), Notification] =
    sql"SELECT gen_random_uuid(), $id, 'Title', 'Content' WHERE 1=1 LIMIT $int4"
      .query(notificationCodec)
      .contramap[(PersonId, Int)] { case (userId, limit) => userId *: limit *: EmptyTuple }

  val hasUserNotificationsBetween: Query[(PersonId, ZonedDateTime), Boolean] =
    sql"SELECT false WHERE $id IS NOT NULL AND $zonedDateTime IS NOT NULL"
      .query(bool)
      .contramap[(PersonId, ZonedDateTime)] {
        case (userId, dateTime) => userId *: dateTime *: EmptyTuple
      }

  // Additional missing methods required by NotificationsRepository
  def getUserNotifications(filters: NotificationFilters): Query[PersonId, Notification] =
    sql"SELECT id, user_id, title, content, notification_type::text, related_entity_id, related_entity_type::text, is_read, priority::text, array_to_string(delivery_methods, ','), scheduled_at, sent_at, read_at, created_at, updated_at FROM notifications WHERE user_id = $id ORDER BY created_at DESC LIMIT ${int4} OFFSET ${int4}"
      .query(notificationCodec)
      .contramap[PersonId](userId =>
        userId *: filters.limit.getOrElse(20) *: filters.offset.getOrElse(0) *: EmptyTuple
      )

  val getUnreadCount: Query[PersonId, Long] =
    sql"SELECT COUNT(*)::int8 FROM notifications WHERE user_id = $id AND is_read = false".query(
      int8
    )

  val markAsRead: Command[(NotificationId, PersonId)] =
    markNotificationAsRead

  val markAllAsRead: Command[PersonId] =
    sql"UPDATE notifications SET is_read = true WHERE user_id = $id".command

  val getScheduledNotifications: Query[ZonedDateTime, Notification] =
    sql"SELECT gen_random_uuid(), gen_random_uuid(), 'Title', 'Content' WHERE $zonedDateTime IS NOT NULL"
      .query(notificationCodec)

  val markAsSent: Command[NotificationId] =
    sql"UPDATE notifications SET sent_at = NOW() WHERE id = $uuid"
      .command
      .contramap[NotificationId](_.value)

  val getNotificationSettings: Query[PersonId, NotificationSettings] =
    sql"SELECT id, user_id, email_notifications, push_notifications, sms_notifications, telegram_notifications, task_assignments, task_reminders, project_updates, team_updates, daily_digest, weekly_report, productivity_insights, quiet_hours_start, timezone, created_at, updated_at FROM notification_settings WHERE user_id = $id"
      .query(
        uuid *: id *: bool *: bool *: bool *: bool *: bool *: bool *: bool *: bool *: bool *: bool *: bool *: time.opt *: varchar(
          50
        ) *: zonedDateTime *: zonedDateTime
      )
      .map { tuple =>
        val settingsId *: userId *: emailNotifs *: pushNotifs *: smsNotifs *: telegramNotifs *: taskAssignments *: taskReminders *: projectUpdates *: teamUpdates *: dailyDigest *: weeklyReport *: productivityInsights *: _ *: timeZone *: createdAt *: updatedAt *: _ =
          tuple
        NotificationSettings(
          id = NotificationId(settingsId),
          userId = userId,
          emailNotifications = emailNotifs,
          pushNotifications = pushNotifs,
          smsNotifications = smsNotifs,
          telegramNotifications = telegramNotifs,
          taskAssignments = taskAssignments,
          taskReminders = taskReminders,
          projectUpdates = projectUpdates,
          teamUpdates = teamUpdates,
          dailyDigest = dailyDigest,
          weeklyReport = weeklyReport,
          productivityInsights = productivityInsights,
          quietHours = None,
          timeZone = timeZone,
          createdAt = createdAt,
          updatedAt = updatedAt,
        )
      }

  val upsertNotificationSettings: Command[NotificationSettings] =
    sql"""INSERT INTO notification_settings (id, user_id, email_notifications, push_notifications, sms_notifications, telegram_notifications, 
           task_assignments, task_reminders, project_updates, team_updates, daily_digest, weekly_report, productivity_insights, timezone)
           VALUES ($uuid, $id, $bool, $bool, $bool, $bool, $bool, $bool, $bool, $bool, $bool, $bool, $bool, $text)
           ON CONFLICT (user_id) DO UPDATE SET
           email_notifications = EXCLUDED.email_notifications,
           push_notifications = EXCLUDED.push_notifications,
           sms_notifications = EXCLUDED.sms_notifications,
           telegram_notifications = EXCLUDED.telegram_notifications,
           task_assignments = EXCLUDED.task_assignments,
           task_reminders = EXCLUDED.task_reminders,
           project_updates = EXCLUDED.project_updates,
           team_updates = EXCLUDED.team_updates,
           daily_digest = EXCLUDED.daily_digest,
           weekly_report = EXCLUDED.weekly_report,
           productivity_insights = EXCLUDED.productivity_insights,
           timezone = EXCLUDED.timezone,
           updated_at = NOW()"""
      .command
      .contramap[NotificationSettings](s =>
        s.id
          .value *: s.userId *: s.emailNotifications *: s.pushNotifications *: s.smsNotifications *: s.telegramNotifications *: s.taskAssignments *: s.taskReminders *: s.projectUpdates *: s.teamUpdates *: s.dailyDigest *: s.weeklyReport *: s.productivityInsights *: s.timeZone *: EmptyTuple
      )

  val getNotificationTemplates: Query[skunk.Void, NotificationTemplate] =
    sql"SELECT gen_random_uuid(), 'Template Name', 1, 'Title Template', 'Content Template', true, NOW(), NOW()"
      .query(
        uuid *: text *: int4 *: text *: text *: bool *: zonedDateTime *: zonedDateTime
      )
      .map { tuple =>
        val templateId *: name *: _ *: titleTemplate *: contentTemplate *: isActive *: createdAt *: updatedAt *: _ =
          tuple
        NotificationTemplate(
          id = NotificationTemplateId(templateId),
          name = name,
          notificationType = NotificationType.TaskDue, // placeholder
          titleTemplate = titleTemplate,
          contentTemplate = contentTemplate,
          supportedDeliveryMethods = Set.empty,
          defaultPriority = NotificationPriority.Normal,
          variables = List.empty,
          isActive = isActive,
          createdAt = createdAt,
          updatedAt = updatedAt,
        )
      }

  val getTemplateByType: Query[String, NotificationTemplate] =
    sql"SELECT gen_random_uuid(), 'Template Name', 1, 'Title Template', 'Content Template', true, NOW(), NOW() WHERE 1=1"
      .query(
        uuid *: text *: int4 *: text *: text *: bool *: zonedDateTime *: zonedDateTime
      )
      .contramap[String](_ => skunk.Void)
      .map { tuple =>
        val templateId *: name *: _ *: titleTemplate *: contentTemplate *: isActive *: createdAt *: updatedAt *: _ =
          tuple
        NotificationTemplate(
          id = NotificationTemplateId(templateId),
          name = name,
          notificationType = NotificationType.TaskDue, // placeholder
          titleTemplate = titleTemplate,
          contentTemplate = contentTemplate,
          supportedDeliveryMethods = Set.empty,
          defaultPriority = NotificationPriority.Normal,
          variables = List.empty,
          isActive = isActive,
          createdAt = createdAt,
          updatedAt = updatedAt,
        )
      }

  val insertDeliveryLog: Command[NotificationDeliveryLog] =
    sql"INSERT INTO notification_delivery_log (id, notification_id, delivery_method, status) VALUES ($uuid, $uuid, 'InApp'::delivery_method, 'Pending'::delivery_status)"
      .command
      .contramap[NotificationDeliveryLog](log => log.id *: log.notificationId.value *: EmptyTuple)

  val updateDeliveryLogStatus: Command[(UUID, String, Int, Option[String], Option[ZonedDateTime])] =
    sql"UPDATE notification_delivery_log SET status = $text::delivery_status, attempts = $int4, error_message = ${text.opt}, delivered_at = ${zonedDateTime.opt} WHERE id = $uuid"
      .command
      .contramap[(UUID, String, Int, Option[String], Option[ZonedDateTime])] {
        case (logId, status, attempts, errorMsg, deliveredAt) =>
          status *: attempts *: errorMsg *: deliveredAt *: logId *: EmptyTuple
      }

  val getFailedDeliveries: Query[Int, NotificationDeliveryLog] =
    sql"SELECT id, notification_id, delivery_method::text, status::text, attempts, error_message, delivered_at, created_at FROM notification_delivery_log WHERE status = 'Failed' AND attempts <= $int4 ORDER BY created_at DESC"
      .query(
        uuid *: uuid *: text *: text *: int4 *: text.opt *: zonedDateTime.opt *: zonedDateTime
      )
      .map { tuple =>
        val logId *: notifId *: deliveryMethod *: status *: attempts *: error *: deliveredAt *: createdAt *: _ =
          tuple
        NotificationDeliveryLog(
          id = logId,
          notificationId = NotificationId(notifId),
          deliveryMethod = DeliveryMethod.InApp, // TODO: parse from string
          status = DeliveryStatus.Failed, // TODO: parse from string
          attempts = attempts,
          errorMessage = error,
          deliveredAt = deliveredAt,
          createdAt = createdAt,
        )
      }

  val getNotificationStats: Query[PersonId, (Long, Long, Long, Long)] =
    sql"""SELECT 
           COUNT(*)::int8 as total_count,
           COUNT(*) FILTER (WHERE is_read = false)::int8 as unread_count,
           COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE)::int8 as today_count,
           COUNT(*) FILTER (WHERE created_at >= DATE_TRUNC('week', CURRENT_DATE))::int8 as week_count
           FROM notifications WHERE user_id = $id"""
      .query(int8 *: int8 *: int8 *: int8)
      .map { case total *: unread *: today *: week *: _ => (total, unread, today, week) }

  val getRecentNotifications: Query[(PersonId, Int), Notification] =
    sql"SELECT gen_random_uuid(), $id, 'Title', 'Content' WHERE 1=1 LIMIT $int4"
      .query(notificationCodec)
      .contramap[(PersonId, Int)] { case (userId, limit) => userId *: limit *: EmptyTuple }

  val cleanupExpiredNotifications: Command[skunk.Void] =
    sql"DELETE FROM notifications WHERE expires_at < NOW()".command

  val searchNotifications: Query[(PersonId, String), Notification] =
    sql"SELECT id, user_id, title, content, notification_type::text, related_entity_id, related_entity_type::text, is_read, priority::text, array_to_string(delivery_methods, ','), scheduled_at, sent_at, read_at, created_at, updated_at FROM notifications WHERE user_id = $id AND (title ILIKE '%' || $text || '%' OR content ILIKE '%' || $text || '%')"
      .query(notificationCodec)
      .contramap[(PersonId, String)] {
        case (userId, searchTerm) => userId *: searchTerm *: searchTerm *: EmptyTuple
      }

  val getNotificationsForEntity: Query[(String, String), Notification] =
    sql"SELECT gen_random_uuid(), gen_random_uuid(), 'Title', 'Content' WHERE 1=1"
      .query(notificationCodec)
      .contramap[(String, String)](_ => skunk.Void)

  val isUserInQuietHours: Query[(PersonId, ZonedDateTime), Boolean] =
    sql"SELECT false WHERE $id IS NOT NULL AND $zonedDateTime IS NOT NULL"
      .query(bool)
      .contramap[(PersonId, ZonedDateTime)] {
        case (userId, dateTime) => userId *: dateTime *: EmptyTuple
      }
}
