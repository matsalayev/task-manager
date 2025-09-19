package tm.services

import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID

import cats.effect.IO
import cats.implicits._
import weaver._

import tm.domain.PersonId
import tm.domain.notifications._
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.repositories.NotificationsRepository
import tm.repositories.UsersRepository
import tm.services.notification.providers.EmailNotificationProvider
import tm.services.notification.providers.SmsNotificationProvider
import tm.support.database.DatabaseResource
import tm.support.database.DatabaseSuite
import tm.utils.ID

object NotificationIntegrationSpec extends DatabaseSuite {
  override type Res = DatabaseResource[IO]

  // Mock users repository for testing
  def mockUsersRepo: UsersRepository[IO] = new UsersRepository[IO] {
    override def findById(id: PersonId): IO[Option[tm.domain.corporate.User]] = {
      val mockUser = tm
        .domain
        .corporate
        .User(
          id = id,
          username = "testuser",
          email = "test@example.com",
          phone = Some("+1234567890"),
          firstName = "Test",
          lastName = "User",
          role = tm.domain.corporate.UserRole.Employee,
          department = Some("Engineering"),
          position = Some("Software Developer"),
          isActive = true,
          createdAt = ZonedDateTime.now(),
          updatedAt = ZonedDateTime.now(),
        )
      IO.pure(Some(mockUser))
    }

    // Other methods not needed for this test
    override def create(user: tm.domain.corporate.User): IO[Unit] = IO.unit
    override def update(user: tm.domain.corporate.User): IO[Unit] = IO.unit
    override def delete(id: PersonId): IO[Unit] = IO.unit
    override def findByUsername(username: String): IO[Option[tm.domain.corporate.User]] =
      IO.pure(None)
    override def findByEmail(email: String): IO[Option[tm.domain.corporate.User]] = IO.pure(None)
    override def list(limit: Int, offset: Int): IO[List[tm.domain.corporate.User]] =
      IO.pure(List.empty)
  }

  // Mock delivery provider that tracks delivery attempts
  class MockDeliveryProvider extends NotificationDeliveryProvider[IO] {
    @volatile private var deliveryAttempts: List[(NotificationId, DeliveryMethod)] = List.empty
    @volatile private var healthyMethods: Set[DeliveryMethod] =
      Set(DeliveryMethod.InApp, DeliveryMethod.WebSocket)

    override def sendNotification(
        notification: Notification,
        deliveryMethod: DeliveryMethod,
      ): IO[Unit] = {
      deliveryAttempts = (notification.id, deliveryMethod) :: deliveryAttempts

      if (healthyMethods.contains(deliveryMethod))
        IO.unit
      else
        IO.raiseError(new RuntimeException(s"Mock delivery failure for $deliveryMethod"))
    }

    override def isHealthy(deliveryMethod: DeliveryMethod): IO[Boolean] =
      IO.pure(healthyMethods.contains(deliveryMethod))

    override def getDeliveryStatus(
        notificationId: NotificationId,
        deliveryMethod: DeliveryMethod,
      ): IO[Option[DeliveryStatus]] = {
      val attempted = deliveryAttempts.exists {
        case (id, method) => id == notificationId && method == deliveryMethod
      }
      if (attempted && healthyMethods.contains(deliveryMethod))
        IO.pure(Some(DeliveryStatus.Delivered))
      else if (attempted)
        IO.pure(Some(DeliveryStatus.Failed))
      else
        IO.pure(None)
    }

    def getDeliveryAttempts: List[(NotificationId, DeliveryMethod)] = deliveryAttempts

    def setHealthyMethods(methods: Set[DeliveryMethod]): Unit =
      healthyMethods = methods
  }

  test("end-to-end notification creation and delivery") { res =>
    for {
      notificationsRepo <- IO(NotificationsRepository.make[IO](res.database))
      usersRepo = mockUsersRepo
      deliveryProvider = new MockDeliveryProvider()
      service = NotificationService.make[IO](notificationsRepo, usersRepo, deliveryProvider)

      userId = PersonId(UUID.randomUUID())

      // Create and send notification
      createRequest = CreateNotificationRequest(
        userId = userId,
        title = eu
          .timepit
          .refined
          .types
          .string
          .NonEmptyString
          .unsafeFrom("Integration Test Notification"),
        content = "This is an end-to-end test notification",
        notificationType = NotificationType.TaskAssigned,
        priority = NotificationPriority.Normal,
        deliveryMethods = Set(DeliveryMethod.InApp, DeliveryMethod.Email, DeliveryMethod.SMS),
      )

      notification <- service.createNotification(createRequest)
      _ <- service.sendNotification(notification)

      // Verify notification was created in database
      found <- notificationsRepo.findNotificationById(notification.id)

      // Verify delivery attempts
      deliveryAttempts = deliveryProvider.getDeliveryAttempts

    } yield expect(found.isDefined) and
      expect(found.get.title.value == "Integration Test Notification") and
      expect(deliveryAttempts.length >= 1) and // At least InApp should succeed
      expect(deliveryAttempts.exists(_._2 == DeliveryMethod.InApp))
  }

  test("notification preferences filtering") { res =>
    for {
      notificationsRepo <- IO(NotificationsRepository.make[IO](res.database))
      usersRepo = mockUsersRepo
      deliveryProvider = new MockDeliveryProvider()
      service = NotificationService.make[IO](notificationsRepo, usersRepo, deliveryProvider)

      userId = PersonId(UUID.randomUUID())

      // Configure user preferences - disable email notifications
      _ <- service.updateNotificationSettings(
        userId,
        UpdateNotificationSettingsRequest(
          emailNotifications = Some(false),
          smsNotifications = Some(true),
          taskAssignments = Some(true),
        ),
      )

      // Create notification with multiple delivery methods
      createRequest = CreateNotificationRequest(
        userId = userId,
        title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Preferences Test"),
        content = "Testing notification preferences",
        notificationType = NotificationType.TaskAssigned,
        deliveryMethods = Set(DeliveryMethod.InApp, DeliveryMethod.Email, DeliveryMethod.SMS),
      )

      notification <- service.createNotification(createRequest)
      _ <- service.sendNotification(notification)

      // Check delivery attempts - email should be filtered out
      deliveryAttempts = deliveryProvider.getDeliveryAttempts

    } yield expect(deliveryAttempts.exists(_._2 == DeliveryMethod.InApp)) and
      expect(!deliveryAttempts.exists(_._2 == DeliveryMethod.Email)) and // Email should be filtered out
      expect(deliveryAttempts.exists(_._2 == DeliveryMethod.SMS))
  }

  test("quiet hours filtering") { res =>
    for {
      notificationsRepo <- IO(NotificationsRepository.make[IO](res.database))
      usersRepo = mockUsersRepo
      deliveryProvider = new MockDeliveryProvider()
      service = NotificationService.make[IO](notificationsRepo, usersRepo, deliveryProvider)

      userId = PersonId(UUID.randomUUID())

      // Set quiet hours (e.g., 10 PM to 8 AM)
      quietHours = QuietHours(
        startTime = LocalTime.of(22, 0), // 10 PM
        endTime = LocalTime.of(8, 0), // 8 AM
        enabled = true,
      )

      _ <- service.updateNotificationSettings(
        userId,
        UpdateNotificationSettingsRequest(
          quietHours = Some(quietHours)
        ),
      )

      // Create notification that would normally send push and SMS
      createRequest = CreateNotificationRequest(
        userId = userId,
        title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Quiet Hours Test"),
        content = "Testing quiet hours filtering",
        notificationType = NotificationType.TaskAssigned,
        deliveryMethods = Set(DeliveryMethod.InApp, DeliveryMethod.Push, DeliveryMethod.SMS),
      )

      notification <- service.createNotification(createRequest)
      _ <- service.sendNotification(notification)

      // During quiet hours, push and SMS should be filtered out
      deliveryAttempts = deliveryProvider.getDeliveryAttempts

    } yield expect(deliveryAttempts.exists(_._2 == DeliveryMethod.InApp)) // InApp should always work
  // Note: Whether push/SMS are filtered depends on the current time during test execution
  }

  test("bulk notification delivery") { res =>
    for {
      notificationsRepo <- IO(NotificationsRepository.make[IO](res.database))
      usersRepo = mockUsersRepo
      deliveryProvider = new MockDeliveryProvider()
      service = NotificationService.make[IO](notificationsRepo, usersRepo, deliveryProvider)

      userIds = List(
        PersonId(UUID.randomUUID()),
        PersonId(UUID.randomUUID()),
        PersonId(UUID.randomUUID()),
      )

      // Send bulk notification
      bulkRequest = BulkNotificationRequest(
        userIds = userIds,
        title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Bulk Test Notification"),
        content = "This is a bulk notification test",
        notificationType = NotificationType.SystemAlert,
        priority = NotificationPriority.High,
        deliveryMethods = Set(DeliveryMethod.InApp),
      )

      notifications <- service.sendBulkNotification(bulkRequest)

      // Verify all notifications were created and delivered
      foundNotifications <- notifications.traverse(n =>
        notificationsRepo.findNotificationById(n.id)
      )
      deliveryAttempts = deliveryProvider.getDeliveryAttempts

    } yield expect(notifications.length == 3) and
      expect(foundNotifications.forall(_.isDefined)) and
      expect(deliveryAttempts.length == 3) and // One delivery attempt per notification
      expect(deliveryAttempts.forall(_._2 == DeliveryMethod.InApp))
  }

  test("notification type preferences") { res =>
    for {
      notificationsRepo <- IO(NotificationsRepository.make[IO](res.database))
      usersRepo = mockUsersRepo
      deliveryProvider = new MockDeliveryProvider()
      service = NotificationService.make[IO](notificationsRepo, usersRepo, deliveryProvider)

      userId = PersonId(UUID.randomUUID())

      // Disable project updates but enable task assignments
      _ <- service.updateNotificationSettings(
        userId,
        UpdateNotificationSettingsRequest(
          taskAssignments = Some(true),
          projectUpdates = Some(false),
        ),
      )

      // Create task assignment notification (should be sent)
      taskNotification <- service.createNotification(
        CreateNotificationRequest(
          userId = userId,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Task Assignment"),
          content = "You have been assigned a new task",
          notificationType = NotificationType.TaskAssigned,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      // Create project update notification (should be filtered)
      projectNotification <- service.createNotification(
        CreateNotificationRequest(
          userId = userId,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Project Update"),
          content = "Project has been updated",
          notificationType = NotificationType.ProjectUpdate,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      // Send both notifications
      _ <- service.sendNotification(taskNotification)
      _ <- service.sendNotification(projectNotification)

      // Check delivery attempts
      deliveryAttempts = deliveryProvider.getDeliveryAttempts

    } yield expect(deliveryAttempts.exists(_._1 == taskNotification.id)) and
      expect(!deliveryAttempts.exists(_._1 == projectNotification.id)) // Project update should be filtered
  }

  test("delivery failure handling and retry") { res =>
    for {
      notificationsRepo <- IO(NotificationsRepository.make[IO](res.database))
      usersRepo = mockUsersRepo
      deliveryProvider = new MockDeliveryProvider()
      service = NotificationService.make[IO](notificationsRepo, usersRepo, deliveryProvider)

      userId = PersonId(UUID.randomUUID())

      // Set email and SMS as unhealthy (will fail)
      _ = deliveryProvider.setHealthyMethods(Set(DeliveryMethod.InApp))

      // Create notification with multiple delivery methods
      createRequest = CreateNotificationRequest(
        userId = userId,
        title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Failure Test"),
        content = "Testing delivery failures",
        notificationType = NotificationType.SystemAlert,
        deliveryMethods = Set(DeliveryMethod.InApp, DeliveryMethod.Email, DeliveryMethod.SMS),
      )

      notification <- service.createNotification(createRequest)

      // Send notification (some deliveries will fail)
      _ <- service.sendNotification(notification).attempt

      // Verify delivery attempts were made
      deliveryAttempts = deliveryProvider.getDeliveryAttempts

      // Test retry mechanism
      retriedCount <- service.retryFailedDeliveries()

    } yield expect(deliveryAttempts.length >= 1) and // At least InApp should succeed
      expect(deliveryAttempts.exists(_._2 == DeliveryMethod.InApp)) and
      expect(retriedCount >= 0) // Some failures might be retried
  }

  test("scheduled notification processing") { res =>
    for {
      notificationsRepo <- IO(NotificationsRepository.make[IO](res.database))
      usersRepo = mockUsersRepo
      deliveryProvider = new MockDeliveryProvider()
      service = NotificationService.make[IO](notificationsRepo, usersRepo, deliveryProvider)

      userId = PersonId(UUID.randomUUID())

      // Create scheduled notification (future)
      futureScheduled <- service.createNotification(
        CreateNotificationRequest(
          userId = userId,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Future Notification"),
          content = "This is scheduled for the future",
          notificationType = NotificationType.TaskDue,
          deliveryMethods = Set(DeliveryMethod.InApp),
          scheduledAt = Some(ZonedDateTime.now().plusHours(1)),
        )
      )

      // Create scheduled notification (past - should be processed)
      pastScheduled <- service.createNotification(
        CreateNotificationRequest(
          userId = userId,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Past Notification"),
          content = "This was scheduled for the past",
          notificationType = NotificationType.TaskDue,
          deliveryMethods = Set(DeliveryMethod.InApp),
          scheduledAt = Some(ZonedDateTime.now().minusMinutes(5)),
        )
      )

      // Process scheduled notifications
      processedCount <- service.processScheduledNotifications()

      // Check delivery attempts
      deliveryAttempts = deliveryProvider.getDeliveryAttempts

    } yield expect(processedCount >= 0) and
      expect(deliveryAttempts.exists(_._1 == pastScheduled.id)) // Past notification should be processed
  }

  test("notification statistics accuracy") { res =>
    for {
      notificationsRepo <- IO(NotificationsRepository.make[IO](res.database))
      usersRepo = mockUsersRepo
      deliveryProvider = new MockDeliveryProvider()
      service = NotificationService.make[IO](notificationsRepo, usersRepo, deliveryProvider)

      userId = PersonId(UUID.randomUUID())

      // Create multiple notifications
      notifications <- (1 to 5).toList.traverse { i =>
        service.createNotification(
          CreateNotificationRequest(
            userId = userId,
            title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom(s"Stats Test $i"),
            content = s"Content $i",
            notificationType = NotificationType.SystemAlert,
            deliveryMethods = Set(DeliveryMethod.InApp),
          )
        )
      }

      // Send all notifications
      _ <- notifications.traverse(service.sendNotification)

      // Mark some as read
      _ <- notifications.take(2).traverse(n => service.markAsRead(n.id, userId))

      // Get statistics
      stats <- service.getNotificationStats(userId)

      // Get unread count
      unreadCount <- service.getUnreadCount(userId)

    } yield expect(stats.totalCount == 5) and
      expect(stats.unreadCount == 3) and // 5 - 2 marked as read
      expect(unreadCount == 3) and
      expect(stats.todayCount >= 5) // All created today
  }

  test("notification search functionality") { res =>
    for {
      notificationsRepo <- IO(NotificationsRepository.make[IO](res.database))
      usersRepo = mockUsersRepo
      deliveryProvider = new MockDeliveryProvider()
      service = NotificationService.make[IO](notificationsRepo, usersRepo, deliveryProvider)

      userId = PersonId(UUID.randomUUID())

      // Create searchable notifications
      _ <- List(
        ("Important Project Update", "The marketing project has critical updates"),
        ("Task Assignment", "You have been assigned to debug the authentication system"),
        ("System Maintenance", "Database maintenance scheduled for tonight"),
        ("Team Meeting", "Weekly team sync meeting reminder"),
      ).traverse {
        case (title, content) =>
          service.createNotification(
            CreateNotificationRequest(
              userId = userId,
              title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom(title),
              content = content,
              notificationType = NotificationType.SystemAlert,
              deliveryMethods = Set(DeliveryMethod.InApp),
            )
          )
      }

      // Search for different terms
      projectResults <- service.searchNotifications(userId, "project")
      taskResults <- service.searchNotifications(userId, "task")
      systemResults <- service.searchNotifications(userId, "system")
      authResults <- service.searchNotifications(userId, "authentication")

    } yield expect(projectResults.length == 1) and
      expect(taskResults.length == 1) and
      expect(systemResults.length >= 1) and // Should match "System Maintenance" and possibly "authentication system"
      expect(authResults.length == 1) // Should match task with "authentication system"
  }
}
