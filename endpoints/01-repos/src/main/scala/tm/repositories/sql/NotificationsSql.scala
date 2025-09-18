package tm.repositories.sql

import java.time.LocalTime
import java.time.ZonedDateTime

import skunk._
import skunk.codec.all._
import skunk.implicits._

import tm.domain.PersonId
import tm.domain.notifications._
import tm.support.skunk.Sql
import tm.support.skunk.codecs._
import tm.support.skunk.syntax.all.skunkSyntaxFragmentOps

private[repositories] object NotificationsSql extends Sql[PersonId] {

  // Notification Codec
  val notificationCodec: Codec[Notification] =
    (uuid *: id *: nes *: text *: nes *: text.opt *: nes.opt *: bool *: nes *: jsonb *: jsonb *: zonedDateTime.opt *: zonedDateTime.opt *: zonedDateTime.opt *: zonedDateTime.opt *: text.opt *: text.opt *: zonedDateTime *: zonedDateTime)
      .to[Notification]

  // Notification Settings Codec
  val notificationSettingsCodec: Codec[NotificationSettings] =
    (uuid *: id *: bool *: bool *: bool *: bool *: bool *: bool *: bool *: bool *: bool *: bool *: bool *: time.opt *: time.opt *: nes.opt *: bool.opt *: bool.opt *: nes *: zonedDateTime *: zonedDateTime)
      .to[NotificationSettings]

  // Notification Template Codec
  val notificationTemplateCodec: Codec[NotificationTemplate] =
    (uuid *: nes *: nes *: nes *: text *: jsonb *: nes *: jsonb *: bool *: zonedDateTime *: zonedDateTime)
      .to[NotificationTemplate]

  // Notification Delivery Log Codec
  val deliveryLogCodec: Codec[NotificationDeliveryLog] =
    (uuid *: uuid *: nes *: nes *: int4 *: text.opt *: zonedDateTime.opt *: zonedDateTime)
      .to[NotificationDeliveryLog]

  // Insert notification
  val insertNotification: Command[Notification] =
    sql"""
      INSERT INTO notifications (id, user_id, title, content, notification_type, related_entity_id, related_entity_type,
                                is_read, priority, delivery_methods, metadata, scheduled_at, sent_at, read_at, expires_at,
                                action_url, action_label, created_at, updated_at)
      VALUES ($notificationCodec)
    """.command

  // Find notification by ID
  val findNotificationById: Query[NotificationId, Notification] =
    sql"""
      SELECT id, user_id, title, content, notification_type, related_entity_id, related_entity_type,
             is_read, priority, delivery_methods, metadata, scheduled_at, sent_at, read_at, expires_at,
             action_url, action_label, created_at, updated_at
      FROM notifications
      WHERE id = ${uuid}
      LIMIT 1
    """.query(notificationCodec)

  // Get user notifications with filters
  def getUserNotifications(filters: NotificationFilters): AppliedFragment = {
    val baseQuery = void"""
      SELECT id, user_id, title, content, notification_type, related_entity_id, related_entity_type,
             is_read, priority, delivery_methods, metadata, scheduled_at, sent_at, read_at, expires_at,
             action_url, action_label, created_at, updated_at,
             COUNT(*) OVER() as total_count
      FROM notifications
      WHERE user_id = ${id}
    """

    val whereConditions: List[Option[AppliedFragment]] = List(
      filters.isRead.map(isRead => sql"is_read = ${bool}"),
      filters.notificationType.map(nType => sql"notification_type = ${nes}"),
      filters.priority.map(priority => sql"priority = ${nes}"),
      filters.fromDate.map(fromDate => sql"created_at >= ${zonedDateTime}"),
      filters.toDate.map(toDate => sql"created_at <= ${zonedDateTime}"),
    )

    val limitOffset = sql"ORDER BY created_at DESC LIMIT ${int4} OFFSET ${int4}"

    baseQuery
      .andOpt(whereConditions)
    |+| limitOffset
  }

  // Get unread notifications count
  val getUnreadCount: Query[PersonId, Long] =
    sql"""
      SELECT COUNT(*)
      FROM notifications
      WHERE user_id = $id AND is_read = FALSE
    """.query(int8)

  // Mark notification as read
  val markAsRead: Command[(NotificationId, PersonId)] =
    sql"""
      UPDATE notifications
      SET is_read = TRUE, read_at = NOW(), updated_at = NOW()
      WHERE id = ${uuid} AND user_id = ${id} AND is_read = FALSE
    """.command

  // Mark all notifications as read for user
  val markAllAsRead: Command[PersonId] =
    sql"""
      UPDATE notifications
      SET is_read = TRUE, read_at = NOW(), updated_at = NOW()
      WHERE user_id = $id AND is_read = FALSE
    """.command

  // Delete notification
  val deleteNotification: Command[(NotificationId, PersonId)] =
    sql"""
      DELETE FROM notifications
      WHERE id = ${uuid} AND user_id = ${id}
    """.command

  // Get scheduled notifications that need to be sent
  val getScheduledNotifications: Query[ZonedDateTime, Notification] =
    sql"""
      SELECT id, user_id, title, content, notification_type, related_entity_id, related_entity_type,
             is_read, priority, delivery_methods, metadata, scheduled_at, sent_at, read_at, expires_at,
             action_url, action_label, created_at, updated_at
      FROM notifications
      WHERE scheduled_at IS NOT NULL
        AND scheduled_at <= ${zonedDateTime}
        AND sent_at IS NULL
      ORDER BY scheduled_at ASC
    """.query(notificationCodec)

  // Mark notification as sent
  val markAsSent: Command[NotificationId] =
    sql"""
      UPDATE notifications
      SET sent_at = NOW(), updated_at = NOW()
      WHERE id = ${uuid}
    """.command

  // Get notification settings for user
  val getNotificationSettings: Query[PersonId, NotificationSettings] =
    sql"""
      SELECT id, user_id, email_notifications, push_notifications, sms_notifications, telegram_notifications,
             task_assignments, task_reminders, project_updates, team_updates, daily_digest, weekly_report,
             productivity_insights, quiet_hours_start, quiet_hours_end, quiet_hours_timezone,
             quiet_hours_weekends_only, quiet_hours_enabled, timezone, created_at, updated_at
      FROM notification_settings
      WHERE user_id = $id
      LIMIT 1
    """.query(notificationSettingsCodec)

  // Upsert notification settings
  val upsertNotificationSettings: Command[NotificationSettings] =
    sql"""
      INSERT INTO notification_settings (id, user_id, email_notifications, push_notifications, sms_notifications,
                                        telegram_notifications, task_assignments, task_reminders, project_updates,
                                        team_updates, daily_digest, weekly_report, productivity_insights,
                                        quiet_hours_start, quiet_hours_end, quiet_hours_timezone,
                                        quiet_hours_weekends_only, quiet_hours_enabled, timezone, created_at, updated_at)
      VALUES ($notificationSettingsCodec)
      ON CONFLICT (user_id)
      DO UPDATE SET
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
        quiet_hours_start = EXCLUDED.quiet_hours_start,
        quiet_hours_end = EXCLUDED.quiet_hours_end,
        quiet_hours_timezone = EXCLUDED.quiet_hours_timezone,
        quiet_hours_weekends_only = EXCLUDED.quiet_hours_weekends_only,
        quiet_hours_enabled = EXCLUDED.quiet_hours_enabled,
        timezone = EXCLUDED.timezone,
        updated_at = EXCLUDED.updated_at
    """.command

  // Get notification templates
  val getNotificationTemplates: Query[Void, NotificationTemplate] =
    sql"""
      SELECT id, name, notification_type, title_template, content_template, supported_delivery_methods,
             default_priority, variables, is_active, created_at, updated_at
      FROM notification_templates
      WHERE is_active = TRUE
      ORDER BY name
    """.query(notificationTemplateCodec)

  // Get template by type
  val getTemplateByType: Query[String, NotificationTemplate] =
    sql"""
      SELECT id, name, notification_type, title_template, content_template, supported_delivery_methods,
             default_priority, variables, is_active, created_at, updated_at
      FROM notification_templates
      WHERE notification_type = ${nes} AND is_active = TRUE
      LIMIT 1
    """.query(notificationTemplateCodec)

  // Insert delivery log
  val insertDeliveryLog: Command[NotificationDeliveryLog] =
    sql"""
      INSERT INTO notification_delivery_log (id, notification_id, delivery_method, status, attempts, error_message, delivered_at, created_at)
      VALUES ($deliveryLogCodec)
    """.command

  // Update delivery log status
  val updateDeliveryLogStatus
      : Command[(java.util.UUID, String, Int, Option[String], Option[ZonedDateTime])] =
    sql"""
      UPDATE notification_delivery_log
      SET status = ${nes}, attempts = ${int4}, error_message = ${text.opt}, delivered_at = ${zonedDateTime.opt}, updated_at = NOW()
      WHERE id = ${uuid}
    """.command

  // Get failed deliveries for retry
  val getFailedDeliveries: Query[Int, NotificationDeliveryLog] =
    sql"""
      SELECT id, notification_id, delivery_method, status, attempts, error_message, delivered_at, created_at
      FROM notification_delivery_log
      WHERE status = 'Failed' AND attempts < ${int4}
      ORDER BY created_at ASC
    """.query(deliveryLogCodec)

  // Get notification statistics
  val getNotificationStats: Query[PersonId, (Long, Long, Long, Long)] =
    sql"""
      SELECT
        COUNT(*) as total_count,
        COUNT(*) FILTER (WHERE is_read = FALSE) as unread_count,
        COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE) as today_count,
        COUNT(*) FILTER (WHERE created_at >= DATE_TRUNC('week', CURRENT_DATE)) as week_count
      FROM notifications
      WHERE user_id = $id
    """.query(int8 *: int8 *: int8 *: int8)

  // Get notifications by type for user
  val getNotificationsByType: Query[(PersonId, String), List[(String, Long)]] =
    sql"""
      SELECT notification_type, COUNT(*)
      FROM notifications
      WHERE user_id = ${id} AND notification_type = ${nes}
      GROUP BY notification_type
    """.query(nes *: int8)

  // Bulk insert notifications
  val bulkInsertNotifications: Command[List[Notification]] =
    sql"""
      INSERT INTO notifications (id, user_id, title, content, notification_type, related_entity_id, related_entity_type,
                                is_read, priority, delivery_methods, metadata, scheduled_at, sent_at, read_at, expires_at,
                                action_url, action_label, created_at, updated_at)
      VALUES ($notificationCodec)
    """.command.contramap(_.toList)

  // Clean up expired notifications
  val cleanupExpiredNotifications: Command[Void] =
    sql"""
      DELETE FROM notifications
      WHERE expires_at IS NOT NULL
        AND expires_at < NOW()
        AND is_read = TRUE
    """.command

  // Get notifications for entities (project/task updates)
  val getNotificationsForEntity: Query[(String, String), Notification] =
    sql"""
      SELECT id, user_id, title, content, notification_type, related_entity_id, related_entity_type,
             is_read, priority, delivery_methods, metadata, scheduled_at, sent_at, read_at, expires_at,
             action_url, action_label, created_at, updated_at
      FROM notifications
      WHERE related_entity_type = ${nes} AND related_entity_id = ${nes}
      ORDER BY created_at DESC
    """.query(notificationCodec)

  // Search notifications
  val searchNotifications: Query[(PersonId, String), Notification] =
    sql"""
      SELECT id, user_id, title, content, notification_type, related_entity_id, related_entity_type,
             is_read, priority, delivery_methods, metadata, scheduled_at, sent_at, read_at, expires_at,
             action_url, action_label, created_at, updated_at
      FROM notifications
      WHERE user_id = ${id}
        AND (title ILIKE '%' || ${nes} || '%' OR content ILIKE '%' || ${nes} || '%')
      ORDER BY created_at DESC
      LIMIT 50
    """.query(notificationCodec)

  // Get recent notifications for dashboard
  val getRecentNotifications: Query[(PersonId, Int), Notification] =
    sql"""
      SELECT id, user_id, title, content, notification_type, related_entity_id, related_entity_type,
             is_read, priority, delivery_methods, metadata, scheduled_at, sent_at, read_at, expires_at,
             action_url, action_label, created_at, updated_at
      FROM notifications
      WHERE user_id = ${id}
      ORDER BY created_at DESC
      LIMIT ${int4}
    """.query(notificationCodec)

  // Check if user is in quiet hours
  val isUserInQuietHours: Query[(PersonId, ZonedDateTime), Boolean] =
    sql"""
      SELECT CASE
        WHEN ns.quiet_hours_enabled = FALSE THEN FALSE
        WHEN ns.quiet_hours_start IS NULL OR ns.quiet_hours_end IS NULL THEN FALSE
        WHEN ns.quiet_hours_weekends_only = TRUE
          AND EXTRACT(DOW FROM ${zonedDateTime} AT TIME ZONE ns.timezone) NOT IN (0, 6) THEN FALSE
        ELSE (${zonedDateTime} AT TIME ZONE ns.timezone)::TIME BETWEEN ns.quiet_hours_start AND ns.quiet_hours_end
      END as is_quiet_hours
      FROM notification_settings ns
      WHERE ns.user_id = ${id}
    """.query(bool)
}
