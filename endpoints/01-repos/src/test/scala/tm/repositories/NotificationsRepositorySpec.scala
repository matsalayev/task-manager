package tm.repositories

import java.time.ZonedDateTime
import java.util.UUID

import cats.effect.IO
import cats.effect.Resource
import cats.implicits._
import skunk.Session
import weaver._

import tm.database.DBSuite
import tm.domain.PersonId
import tm.domain.notifications._
import tm.utils.ID

object NotificationsRepositorySpec extends DBSuite {
  override def schemaName: String = "public"
  override def beforeAll(implicit session: Resource[IO, Session[IO]]): IO[Unit] = data.setup

  test("create and find notification") { implicit session =>
    for {
      repo <- IO(NotificationsRepository.make[IO])

      // Create test data
      notificationId <- ID.make[IO, NotificationId]
      userId = data.people.person1.id

      notification = Notification(
        id = notificationId,
        userId = userId,
        title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Test Notification"),
        content = "This is a test notification content",
        notificationType = NotificationType.TaskAssigned,
        relatedEntityId = Some("task-123"),
        relatedEntityType = Some(EntityType.Task),
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

  test("get user notifications with filters") { implicit session =>
    for {
      repo <- IO(NotificationsRepository.make[IO])

      userId = data.people.person2.id

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
            relatedEntityType = Some(EntityType.Task),
            isRead = i <= 2, // First 2 are read
            priority = if (i == 1) NotificationPriority.High else NotificationPriority.Normal,
            deliveryMethods = Set(DeliveryMethod.InApp),
            metadata = Map.empty,
            scheduledAt = None,
            sentAt = Some(ZonedDateTime.now().minusHours(i.toLong)),
            readAt = if (i <= 2) Some(ZonedDateTime.now().minusMinutes((i * 10).toLong)) else None,
            expiresAt = None,
            actionUrl = None,
            actionLabel = None,
            createdAt = ZonedDateTime.now().minusHours(i.toLong),
            updatedAt = ZonedDateTime.now().minusHours(i.toLong),
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

    } yield expect(allNotifications.length >= 5) and
      expect(totalCount >= 5) and
      expect(unreadNotifications.length >= 3) and // At least 3 unread from this test
      expect(unreadTotal >= 3) and
      expect(taskNotifications.length >= 2) and // At least 2 TaskAssigned from this test
      expect(taskTotal >= 2) and
      expect(firstPage.length == 2) and // Pagination still works
      expect(unreadCount >= 3) // At least 3 unread for this user
  }

  test("mark notifications as read") { implicit session =>
    for {
      repo <- IO(NotificationsRepository.make[IO])

      userId = data.people.person2.id
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

      // Verify it's unread initially
      initialNotification <- repo.findNotificationById(notificationId)

      // Mark as read
      _ <- repo.markAsRead(notificationId, userId)

      // Verify it's read
      found <- repo.findNotificationById(notificationId)

    } yield expect(initialNotification.get.isRead == false) and
      expect(initialNotification.get.readAt.isEmpty) and
      expect(found.get.isRead == true) and
      expect(found.get.readAt.isDefined)
  }

  test("notification settings CRUD") { implicit session =>
    for {
      repo <- IO(NotificationsRepository.make[IO])

      userId = data.people.person1.id
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
            timeZone = "America/New_York",
            weekendsOnly = false,
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

  test("delivery logs") { implicit session =>
    for {
      repo <- IO(NotificationsRepository.make[IO])

      notificationId <- ID.make[IO, NotificationId]
      userId = data.people.person2.id
      logId = UUID.randomUUID()

      // First create the notification
      notification = Notification(
        id = notificationId,
        userId = userId,
        title = eu
          .timepit
          .refined
          .types
          .string
          .NonEmptyString
          .unsafeFrom("Test Notification for Delivery"),
        content = "This notification has delivery logs",
        notificationType = NotificationType.TaskAssigned,
        relatedEntityId = None,
        relatedEntityType = None,
        isRead = false,
        priority = NotificationPriority.Normal,
        deliveryMethods = Set(DeliveryMethod.Email),
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

  test("search notifications") { implicit session =>
    for {
      repo <- IO(NotificationsRepository.make[IO])

      userId = data.people.person1.id

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

  test("notification statistics") { implicit session =>
    for {
      repo <- IO(NotificationsRepository.make[IO])

      userId = data.people.person2.id

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
            sentAt = Some(ZonedDateTime.now().minusHours(i.toLong)),
            readAt = if (i <= 3) Some(ZonedDateTime.now().minusMinutes((i * 5).toLong)) else None,
            expiresAt = None,
            actionUrl = None,
            actionLabel = None,
            createdAt =
              if (i <= 5) ZonedDateTime.now().minusHours(i.toLong)
              else ZonedDateTime.now().minusDays(i.toLong), // Some from today, some older
            updatedAt = ZonedDateTime.now().minusHours(i.toLong),
          )
          _ <- repo.createNotification(notification)
        } yield ()
      }

      // Get statistics
      stats <- repo.getNotificationStats(userId)

    } yield expect(stats.totalCount >= 10) and // At least 10 notifications (could be more from other tests)
      expect(stats.unreadCount >= 7) and // At least 7 unread notifications
      expect(stats.todayCount >= 5) and // At least 5 created today
      expect(stats.weekCount >= 10) // At least 10 within a week
  }
}
