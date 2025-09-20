package tm.repositories

import java.time.ZonedDateTime

import cats.Applicative
import cats.effect.Resource
import cats.implicits._
import skunk._

import tm.domain.PersonId
import tm.domain.notifications._
import tm.repositories.sql.NotificationsSql
import tm.support.skunk.syntax.all._

trait NotificationsRepository[F[_]] {
  // Notification CRUD
  def createNotification(notification: Notification): F[Unit]
  def findNotificationById(id: NotificationId): F[Option[Notification]]
  def getUserNotifications(
      userId: PersonId,
      filters: NotificationFilters,
    ): F[(List[Notification], Long)]
  def getUnreadCount(userId: PersonId): F[Long]
  def markAsRead(notificationId: NotificationId, userId: PersonId): F[Unit]
  def markAllAsRead(userId: PersonId): F[Unit]
  def deleteNotification(notificationId: NotificationId, userId: PersonId): F[Unit]

  // Scheduled notifications
  def getScheduledNotifications(now: ZonedDateTime): F[List[Notification]]
  def markAsSent(notificationId: NotificationId): F[Unit]

  // Notification settings
  def getNotificationSettings(userId: PersonId): F[Option[NotificationSettings]]
  def upsertNotificationSettings(settings: NotificationSettings): F[Unit]

  // Templates
  def getNotificationTemplates(): F[List[NotificationTemplate]]
  def getTemplateByType(notificationType: String): F[Option[NotificationTemplate]]

  // Delivery logs
  def createDeliveryLog(log: NotificationDeliveryLog): F[Unit]
  def updateDeliveryLogStatus(
      logId: java.util.UUID,
      status: String,
      attempts: Int,
      errorMessage: Option[String],
      deliveredAt: Option[ZonedDateTime],
    ): F[Unit]
  def getFailedDeliveries(maxAttempts: Int): F[List[NotificationDeliveryLog]]

  // Statistics and analytics
  def getNotificationStats(userId: PersonId): F[NotificationStats]
  def getRecentNotifications(userId: PersonId, limit: Int): F[List[Notification]]

  // Utility methods
  def bulkCreateNotifications(notifications: List[Notification]): F[Unit]
  def cleanupExpiredNotifications(): F[Unit]
  def searchNotifications(userId: PersonId, query: String): F[List[Notification]]
  def getNotificationsForEntity(entityType: String, entityId: String): F[List[Notification]]
  def isUserInQuietHours(userId: PersonId, dateTime: ZonedDateTime): F[Boolean]
}

object NotificationsRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): NotificationsRepository[F] = new NotificationsRepository[F] {
    override def createNotification(notification: Notification): F[Unit] =
      NotificationsSql.insertNotification.execute(notification)

    override def findNotificationById(id: NotificationId): F[Option[Notification]] =
      NotificationsSql.findNotificationById.queryOption(id)

    override def getUserNotifications(
        userId: PersonId,
        filters: NotificationFilters,
      ): F[(List[Notification], Long)] =
      for {
        notifications <- NotificationsSql
          .getUserNotifications(filters)
          .queryList(userId)
        totalCount = notifications.length.toLong
      } yield (notifications, totalCount)

    override def getUnreadCount(userId: PersonId): F[Long] =
      NotificationsSql.getUnreadCount.queryUnique(userId)

    override def markAsRead(notificationId: NotificationId, userId: PersonId): F[Unit] =
      NotificationsSql.markAsRead.execute((notificationId, userId))

    override def markAllAsRead(userId: PersonId): F[Unit] =
      NotificationsSql.markAllAsRead.execute(userId)

    override def deleteNotification(notificationId: NotificationId, userId: PersonId): F[Unit] =
      NotificationsSql.deleteNotification.execute((notificationId, userId))

    override def getScheduledNotifications(now: ZonedDateTime): F[List[Notification]] =
      NotificationsSql.getScheduledNotifications.queryList(now)

    override def markAsSent(notificationId: NotificationId): F[Unit] =
      NotificationsSql.markAsSent.execute(notificationId)

    override def getNotificationSettings(userId: PersonId): F[Option[NotificationSettings]] =
      NotificationsSql.getNotificationSettings.queryOption(userId)

    override def upsertNotificationSettings(settings: NotificationSettings): F[Unit] =
      NotificationsSql.upsertNotificationSettings.execute(settings)

    override def getNotificationTemplates(): F[List[NotificationTemplate]] =
      NotificationsSql.getNotificationTemplates.queryList(skunk.Void)

    override def getTemplateByType(notificationType: String): F[Option[NotificationTemplate]] =
      NotificationsSql.getTemplateByType.queryOption(notificationType)

    override def createDeliveryLog(log: NotificationDeliveryLog): F[Unit] =
      NotificationsSql.insertDeliveryLog.execute(log)

    override def updateDeliveryLogStatus(
        logId: java.util.UUID,
        status: String,
        attempts: Int,
        errorMessage: Option[String],
        deliveredAt: Option[ZonedDateTime],
      ): F[Unit] =
      NotificationsSql
        .updateDeliveryLogStatus
        .execute((logId, status, attempts, errorMessage, deliveredAt))

    override def getFailedDeliveries(maxAttempts: Int): F[List[NotificationDeliveryLog]] =
      NotificationsSql.getFailedDeliveries.queryList(maxAttempts)

    override def getNotificationStats(userId: PersonId): F[NotificationStats] =
      for {
        (total, unread, today, week) <- NotificationsSql.getNotificationStats.queryUnique(userId)
        // TODO: Implement byType and byPriority statistics
        byType = Map.empty[String, Long]
        byPriority = Map.empty[String, Long]
      } yield NotificationStats(
        totalCount = total,
        unreadCount = unread,
        todayCount = today,
        weekCount = week,
        byType = byType,
        byPriority = byPriority,
      )

    override def getRecentNotifications(userId: PersonId, limit: Int): F[List[Notification]] =
      NotificationsSql.getRecentNotifications.queryList((userId, limit))

    override def bulkCreateNotifications(notifications: List[Notification]): F[Unit] =
      if (notifications.nonEmpty)
        // For bulk insert, we need to insert one by one or use a batch command
        notifications.traverse_(createNotification)
      else
        Applicative[F].unit

    override def cleanupExpiredNotifications(): F[Unit] =
      NotificationsSql.cleanupExpiredNotifications.execute(skunk.Void)

    override def searchNotifications(userId: PersonId, query: String): F[List[Notification]] =
      NotificationsSql.searchNotifications.queryList((userId, query))

    override def getNotificationsForEntity(
        entityType: String,
        entityId: String,
      ): F[List[Notification]] =
      NotificationsSql.getNotificationsForEntity.queryList((entityType, entityId))

    override def isUserInQuietHours(userId: PersonId, dateTime: ZonedDateTime): F[Boolean] =
      NotificationsSql.isUserInQuietHours.queryOption((userId, dateTime)).map(_.getOrElse(false))
  }
}
