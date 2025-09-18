package tm.repositories

import java.time.ZonedDateTime
import java.util.UUID

import cats.effect.IO
import weaver._

import tm.domain.PersonId
import tm.domain.notifications._
import tm.support.database.DatabaseResource
import tm.support.database.DatabaseSuite
import tm.utils.ID

object NotificationsRepositorySpec extends DatabaseSuite {
  override type Res = DatabaseResource[IO]

  test("create and find notification") { res =>
    for {
      repo <- IO(NotificationsRepository.make[IO](res.database))

      // Create test data
      notificationId <- ID.make[IO, NotificationId]
      userId = PersonId(UUID.randomUUID())

      notification = Notification(
        id = notificationId,
        userId = userId,
        title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Test Notification"),
        content = "This is a test notification content",
        notificationType = NotificationType.TaskAssigned,
        relatedEntityId = Some("task-123"),
        relatedEntityType = Some("Task"),
        isRead = false,
        priority = NotificationPriority.Normal,
        deliveryMethods = Set(DeliveryMethod.InApp, DeliveryMethod.Email),
        metadata = Map("key" -> "value"),
        scheduledAt = None,
        sentAt = None,
        readAt = None,
        expiresAt = None,
        actionUrl = Some("https://app.example.com/tasks/123"),
        actionLabel = Some("View Task"),
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
      )

      // Create notification
      _ <- repo.createNotification(notification)

      // Find notification
      found <- repo.findNotificationById(notificationId)

    } yield expect(found.isDefined) and
      expect(found.get.id == notificationId) and
      expect(found.get.userId == userId) and
      expect(found.get.title.value == "Test Notification") and
      expect(found.get.notificationType == NotificationType.TaskAssigned) and
      expect(found.get.priority == NotificationPriority.Normal) and
      expect(found.get.deliveryMethods.contains(DeliveryMethod.InApp)) and
      expect(found.get.deliveryMethods.contains(DeliveryMethod.Email))
  }

  test("get user notifications with filters") { res =>
    for {
      repo <- IO(NotificationsRepository.make[IO](res.database))

      userId = PersonId(UUID.randomUUID())

      // Create multiple notifications
      notifications <- (1 to 5).toList.traverse { i =>
        for {
          id <- ID.make[IO, NotificationId]
          notification = Notification(
            id = id,
            userId = userId,
            title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom(s"Notification $i"),
            content = s"Content for notification $i",
            notificationType =
              if (i % 2 == 0) NotificationType.TaskAssigned else NotificationType.ProjectUpdate,
            relatedEntityId = Some(s"entity-$i"),
            relatedEntityType = Some("Task"),
            isRead = i <= 2, // First 2 are read
            priority = if (i == 1) NotificationPriority.High else NotificationPriority.Normal,
            deliveryMethods = Set(DeliveryMethod.InApp),
            metadata = Map.empty,
            scheduledAt = None,
            sentAt = Some(ZonedDateTime.now().minusHours(i)),
            readAt = if (i <= 2) Some(ZonedDateTime.now().minusMinutes(i * 10)) else None,
            expiresAt = None,
            actionUrl = None,
            actionLabel = None,
            createdAt = ZonedDateTime.now().minusHours(i),
            updatedAt = ZonedDateTime.now().minusHours(i),
          )
          _ <- repo.createNotification(notification)
        } yield notification
      }

      // Test: Get all notifications
      (allNotifications, totalCount) <- repo.getUserNotifications(userId, NotificationFilters())

      // Test: Get only unread notifications
      (unreadNotifications, unreadTotal) <- repo.getUserNotifications(
        userId,
        NotificationFilters(isRead = Some(false)),
      )

      // Test: Get notifications by type
      (taskNotifications, taskTotal) <- repo.getUserNotifications(
        userId,
        NotificationFilters(notificationType = Some(NotificationType.TaskAssigned)),
      )

      // Test: Get with pagination
      (firstPage, _) <- repo.getUserNotifications(
        userId,
        NotificationFilters(limit = Some(2), offset = Some(0)),
      )

      // Test: Get unread count
      unreadCount <- repo.getUnreadCount(userId)

    } yield expect(allNotifications.length == 5) and
      expect(totalCount == 5) and
      expect(unreadNotifications.length == 3) and // 3 unread (notifications 3, 4, 5)
      expect(unreadTotal == 3) and
      expect(taskNotifications.length == 2) and // notifications 2, 4 are TaskAssigned
      expect(taskTotal == 2) and
      expect(firstPage.length == 2) and
      expect(unreadCount == 3)
  }

  test("mark notifications as read") { res =>
    for {
      repo <- IO(NotificationsRepository.make[IO](res.database))

      userId = PersonId(UUID.randomUUID())
      notificationId <- ID.make[IO, NotificationId]

      notification = Notification(
        id = notificationId,
        userId = userId,
        title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Unread Notification"),
        content = "This should be marked as read",
        notificationType = NotificationType.SystemAlert,
        relatedEntityId = None,
        relatedEntityType = None,
        isRead = false,
        priority = NotificationPriority.Normal,
        deliveryMethods = Set(DeliveryMethod.InApp),
        metadata = Map.empty,
        scheduledAt = None,
        sentAt = None,
        readAt = None,
        expiresAt = None,
        actionUrl = None,
        actionLabel = None,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
      )

      _ <- repo.createNotification(notification)

      // Verify it's unread
      unreadCountBefore <- repo.getUnreadCount(userId)

      // Mark as read
      _ <- repo.markAsRead(notificationId, userId)

      // Verify it's read
      unreadCountAfter <- repo.getUnreadCount(userId)
      found <- repo.findNotificationById(notificationId)

    } yield expect(unreadCountBefore == 1) and
      expect(unreadCountAfter == 0) and
      expect(found.get.isRead == true) and
      expect(found.get.readAt.isDefined)
  }

  test("notification settings CRUD") { res =>
    for {
      repo <- IO(NotificationsRepository.make[IO](res.database))

      userId = PersonId(UUID.randomUUID())
      settingsId <- ID.make[IO, NotificationId]

      settings = NotificationSettings(
        id = settingsId,
        userId = userId,
        emailNotifications = true,
        pushNotifications = false,
        smsNotifications = true,
        telegramNotifications = false,
        taskAssignments = true,
        taskReminders = false,
        projectUpdates = true,
        teamUpdates = false,
        dailyDigest = true,
        weeklyReport = false,
        productivityInsights = true,
        quietHours = Some(
          QuietHours(
            startTime = java.time.LocalTime.of(22, 0),
            endTime = java.time.LocalTime.of(8, 0),
            enabled = true,
          )
        ),
        timeZone = "America/New_York",
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
      )

      // Create settings
      _ <- repo.upsertNotificationSettings(settings)

      // Get settings
      found <- repo.getNotificationSettings(userId)

      // Update settings
      updatedSettings = settings.copy(
        emailNotifications = false,
        pushNotifications = true,
        timeZone = "Europe/London",
      )
      _ <- repo.upsertNotificationSettings(updatedSettings)

      // Get updated settings
      foundUpdated <- repo.getNotificationSettings(userId)

    } yield expect(found.isDefined) and
      expect(found.get.userId == userId) and
      expect(found.get.emailNotifications == true) and
      expect(found.get.pushNotifications == false) and
      expect(found.get.timeZone == "America/New_York") and
      expect(foundUpdated.get.emailNotifications == false) and
      expect(foundUpdated.get.pushNotifications == true) and
      expect(foundUpdated.get.timeZone == "Europe/London")
  }

  test("delivery logs") { res =>
    for {
      repo <- IO(NotificationsRepository.make[IO](res.database))

      notificationId <- ID.make[IO, NotificationId]
      logId = UUID.randomUUID()

      deliveryLog = NotificationDeliveryLog(
        id = logId,
        notificationId = notificationId,
        deliveryMethod = DeliveryMethod.Email,
        status = DeliveryStatus.Pending,
        attempts = 1,
        errorMessage = None,
        deliveredAt = None,
        createdAt = ZonedDateTime.now(),
      )

      // Create delivery log
      _ <- repo.createDeliveryLog(deliveryLog)

      // Update status to failed
      _ <- repo.updateDeliveryLogStatus(
        logId,
        DeliveryStatus.Failed.toString,
        2,
        Some("SMTP connection failed"),
        None,
      )

      // Get failed deliveries
      failedDeliveries <- repo.getFailedDeliveries(maxAttempts = 3)

    } yield expect(failedDeliveries.nonEmpty) and
      expect(failedDeliveries.head.id == logId) and
      expect(failedDeliveries.head.attempts == 2) and
      expect(failedDeliveries.head.errorMessage.contains("SMTP connection failed"))
  }

  test("search notifications") { res =>
    for {
      repo <- IO(NotificationsRepository.make[IO](res.database))

      userId = PersonId(UUID.randomUUID())

      // Create searchable notifications
      searchableNotifications <- List(
        ("Important Task Assignment", "You have been assigned to complete the quarterly report"),
        ("Project Update", "The marketing campaign project has been updated"),
        ("System Alert", "Database maintenance scheduled for tonight"),
      ).traverse {
        case (title, content) =>
          for {
            id <- ID.make[IO, NotificationId]
            notification = Notification(
              id = id,
              userId = userId,
              title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom(title),
              content = content,
              notificationType = NotificationType.SystemAlert,
              relatedEntityId = None,
              relatedEntityType = None,
              isRead = false,
              priority = NotificationPriority.Normal,
              deliveryMethods = Set(DeliveryMethod.InApp),
              metadata = Map.empty,
              scheduledAt = None,
              sentAt = None,
              readAt = None,
              expiresAt = None,
              actionUrl = None,
              actionLabel = None,
              createdAt = ZonedDateTime.now(),
              updatedAt = ZonedDateTime.now(),
            )
            _ <- repo.createNotification(notification)
          } yield notification
      }

      // Search for "project"
      projectResults <- repo.searchNotifications(userId, "project")

      // Search for "task"
      taskResults <- repo.searchNotifications(userId, "task")

      // Search for "database"
      databaseResults <- repo.searchNotifications(userId, "database")

    } yield expect(projectResults.length == 1) and
      expect(projectResults.head.title.value.contains("Project")) and
      expect(taskResults.length == 1) and
      expect(taskResults.head.title.value.contains("Task")) and
      expect(databaseResults.length == 1) and
      expect(databaseResults.head.content.contains("Database"))
  }

  test("notification statistics") { res =>
    for {
      repo <- IO(NotificationsRepository.make[IO](res.database))

      userId = PersonId(UUID.randomUUID())

      // Create notifications with different statuses and dates
      _ <- (1 to 10).toList.traverse { i =>
        for {
          id <- ID.make[IO, NotificationId]
          notification = Notification(
            id = id,
            userId = userId,
            title =
              eu.timepit.refined.types.string.NonEmptyString.unsafeFrom(s"Stats Notification $i"),
            content = s"Content $i",
            notificationType = NotificationType.SystemAlert,
            relatedEntityId = None,
            relatedEntityType = None,
            isRead = i <= 3, // First 3 are read
            priority = NotificationPriority.Normal,
            deliveryMethods = Set(DeliveryMethod.InApp),
            metadata = Map.empty,
            scheduledAt = None,
            sentAt = Some(ZonedDateTime.now().minusHours(i)),
            readAt = if (i <= 3) Some(ZonedDateTime.now().minusMinutes(i * 5)) else None,
            expiresAt = None,
            actionUrl = None,
            actionLabel = None,
            createdAt =
              if (i <= 5) ZonedDateTime.now().minusHours(i) else ZonedDateTime.now().minusDays(i), // Some from today, some older
            updatedAt = ZonedDateTime.now().minusHours(i),
          )
          _ <- repo.createNotification(notification)
        } yield ()
      }

      // Get statistics
      stats <- repo.getNotificationStats(userId)

    } yield expect(stats.totalCount == 10) and
      expect(stats.unreadCount == 7) and // 7 unread notifications
      expect(stats.todayCount >= 5) and // At least 5 created today
      expect(stats.weekCount == 10) // All 10 within a week
  }
}
