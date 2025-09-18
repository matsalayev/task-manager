package tm.endpoint.routes

import java.time.ZonedDateTime
import java.util.UUID

import cats.effect.IO
import cats.implicits._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import weaver._

import tm.domain.PersonId
import tm.domain.auth.AuthedUser
import tm.domain.notifications._
import tm.services.NotificationService
import tm.support.http4s.utils.Routes

object NotificationRoutesSpec extends SimpleIOSuite {

  // Mock notification service for testing
  def mockNotificationService: NotificationService[IO] = new NotificationService[IO] {
    private var notifications = Map.empty[NotificationId, Notification]
    private var settings = Map.empty[PersonId, NotificationSettings]

    override def createNotification(request: CreateNotificationRequest): IO[Notification] = {
      val id = NotificationId(UUID.randomUUID())
      val notification = Notification(
        id = id,
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
        expiresAt = None,
        actionUrl = request.actionUrl,
        actionLabel = request.actionLabel,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
      )
      notifications = notifications + (id -> notification)
      IO.pure(notification)
    }

    override def sendNotification(notification: Notification): IO[Unit] = IO.unit

    override def getUserNotifications(
        userId: PersonId,
        filters: NotificationFilters,
      ): IO[(List[Notification], Long)] = {
      val userNotifications = notifications.values.filter(_.userId == userId).toList
      val filtered = filters.isRead match {
        case Some(read) => userNotifications.filter(_.isRead == read)
        case None => userNotifications
      }
      IO.pure((filtered, filtered.length.toLong))
    }

    override def getUnreadNotifications(userId: PersonId): IO[List[Notification]] =
      IO.pure(notifications.values.filter(n => n.userId == userId && !n.isRead).toList)

    override def getUnreadCount(userId: PersonId): IO[Long] =
      IO.pure(notifications.values.count(n => n.userId == userId && !n.isRead).toLong)

    override def markAsRead(notificationId: NotificationId, userId: PersonId): IO[Unit] =
      notifications.get(notificationId) match {
        case Some(notification) if notification.userId == userId =>
          notifications = notifications + (notificationId -> notification.copy(
            isRead = true,
            readAt = Some(ZonedDateTime.now()),
          ))
          IO.unit
        case _ => IO.unit
      }

    override def markAllAsRead(userId: PersonId): IO[Unit] = {
      notifications = notifications.map {
        case (id, notification) =>
          if (notification.userId == userId)
            id -> notification.copy(isRead = true, readAt = Some(ZonedDateTime.now()))
          else
            id -> notification
      }
      IO.unit
    }

    override def deleteNotification(notificationId: NotificationId, userId: PersonId): IO[Unit] =
      notifications.get(notificationId) match {
        case Some(notification) if notification.userId == userId =>
          notifications = notifications - notificationId
          IO.unit
        case _ => IO.unit
      }

    override def getNotificationSettings(userId: PersonId): IO[NotificationSettings] =
      settings.get(userId) match {
        case Some(s) => IO.pure(s)
        case None =>
          val defaultSettings = NotificationSettings(
            id = NotificationId(UUID.randomUUID()),
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
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now(),
          )
          settings = settings + (userId -> defaultSettings)
          IO.pure(defaultSettings)
      }

    override def updateNotificationSettings(
        userId: PersonId,
        request: UpdateNotificationSettingsRequest,
      ): IO[NotificationSettings] =
      for {
        current <- getNotificationSettings(userId)
        updated = current.copy(
          emailNotifications = request.emailNotifications.getOrElse(current.emailNotifications),
          pushNotifications = request.pushNotifications.getOrElse(current.pushNotifications),
          smsNotifications = request.smsNotifications.getOrElse(current.smsNotifications),
          telegramNotifications =
            request.telegramNotifications.getOrElse(current.telegramNotifications),
          taskAssignments = request.taskAssignments.getOrElse(current.taskAssignments),
          taskReminders = request.taskReminders.getOrElse(current.taskReminders),
          projectUpdates = request.projectUpdates.getOrElse(current.projectUpdates),
          teamUpdates = request.teamUpdates.getOrElse(current.teamUpdates),
          dailyDigest = request.dailyDigest.getOrElse(current.dailyDigest),
          weeklyReport = request.weeklyReport.getOrElse(current.weeklyReport),
          productivityInsights =
            request.productivityInsights.getOrElse(current.productivityInsights),
          quietHours = request.quietHours.orElse(current.quietHours),
          timeZone = request.timeZone.getOrElse(current.timeZone),
          updatedAt = ZonedDateTime.now(),
        )
        _ = settings = settings + (userId -> updated)
      } yield updated

    override def sendBulkNotification(request: BulkNotificationRequest): IO[List[Notification]] =
      request.userIds.traverse { userId =>
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

    override def processScheduledNotifications(): IO[Int] = IO.pure(0)

    override def getNotificationStats(userId: PersonId): IO[NotificationStats] = {
      val userNotifications = notifications.values.filter(_.userId == userId)
      IO.pure(
        NotificationStats(
          totalCount = userNotifications.size.toLong,
          unreadCount = userNotifications.count(!_.isRead).toLong,
          todayCount = userNotifications.size.toLong, // Simplified
          weekCount = userNotifications.size.toLong, // Simplified
          byType = Map.empty,
          byPriority = Map.empty,
        )
      )
    }

    override def searchNotifications(userId: PersonId, query: String): IO[List[Notification]] = {
      val userNotifications = notifications.values.filter(_.userId == userId)
      val matching = userNotifications.filter { n =>
        n.title.value.toLowerCase.contains(query.toLowerCase) ||
        n.content.toLowerCase.contains(query.toLowerCase)
      }
      IO.pure(matching.toList)
    }

    override def sendTemplatedNotification(
        userId: PersonId,
        notificationType: NotificationType,
        variables: Map[String, String],
        deliveryMethods: Set[DeliveryMethod],
      ): IO[Notification] =
      createNotification(
        CreateNotificationRequest(
          userId = userId,
          title =
            eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Templated Notification"),
          content = "Templated content",
          notificationType = notificationType,
          deliveryMethods = deliveryMethods,
        )
      )

    override def sendSystemNotification(
        userIds: List[PersonId],
        title: String,
        content: String,
        priority: NotificationPriority,
      ): IO[List[Notification]] =
      userIds.traverse { userId =>
        createNotification(
          CreateNotificationRequest(
            userId = userId,
            title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom(title),
            content = content,
            notificationType = NotificationType.SystemAlert,
            priority = priority,
            deliveryMethods = Set(DeliveryMethod.InApp),
          )
        )
      }

    override def retryFailedDeliveries(): IO[Int] = IO.pure(0)

    override def cleanupExpiredNotifications(): IO[Int] = IO.pure(0)

    override def shouldSendNotification(
        userId: PersonId,
        notificationType: NotificationType,
        deliveryMethod: DeliveryMethod,
      ): IO[Boolean] =
      IO.pure(true)

    override def isUserInQuietHours(userId: PersonId): IO[Boolean] = IO.pure(false)
  }

  def testUser: AuthedUser = AuthedUser(
    id = PersonId(UUID.randomUUID()),
    username = "testuser",
    role = tm.domain.corporate.UserRole.Employee,
  )

  test("GET /notifications - get user notifications") {
    val service = mockNotificationService
    val routes = NotificationRoutes[IO](service)
    val app = routes.`private`.orNotFound

    // Create a test notification first
    for {
      _ <- service.createNotification(
        CreateNotificationRequest(
          userId = testUser.id,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Test Notification"),
          content = "Test content",
          notificationType = NotificationType.TaskAssigned,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      request <- IO.pure(
        Request[IO](Method.GET, uri"/notifications")
          .withAuthenticatedUser(testUser)
      )

      response <- app.run(request)
      body <- response.as[String]

    } yield expect(response.status == Status.Ok) and
      expect(body.contains("Test Notification"))
  }

  test("GET /notifications/unread - get unread notifications") {
    val service = mockNotificationService
    val routes = NotificationRoutes[IO](service)
    val app = routes.`private`.orNotFound

    for {
      // Create test notifications
      _ <- service.createNotification(
        CreateNotificationRequest(
          userId = testUser.id,
          title =
            eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Unread Notification 1"),
          content = "Content 1",
          notificationType = NotificationType.TaskAssigned,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      _ <- service.createNotification(
        CreateNotificationRequest(
          userId = testUser.id,
          title =
            eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Unread Notification 2"),
          content = "Content 2",
          notificationType = NotificationType.ProjectUpdate,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      request <- IO.pure(
        Request[IO](Method.GET, uri"/notifications/unread")
          .withAuthenticatedUser(testUser)
      )

      response <- app.run(request)
      notifications <- response.as[List[Notification]]

    } yield expect(response.status == Status.Ok) and
      expect(notifications.length == 2) and
      expect(notifications.forall(!_.isRead))
  }

  test("GET /notifications/unread-count - get unread count") {
    val service = mockNotificationService
    val routes = NotificationRoutes[IO](service)
    val app = routes.`private`.orNotFound

    for {
      // Create test notifications
      _ <- service.createNotification(
        CreateNotificationRequest(
          userId = testUser.id,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Unread Notification"),
          content = "Content",
          notificationType = NotificationType.TaskAssigned,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      request <- IO.pure(
        Request[IO](Method.GET, uri"/notifications/unread-count")
          .withAuthenticatedUser(testUser)
      )

      response <- app.run(request)
      body <- response.as[Map[String, Long]]

    } yield expect(response.status == Status.Ok) and
      expect(body.get("unreadCount").contains(1L))
  }

  test("POST /notifications/{id}/read - mark notification as read") {
    val service = mockNotificationService
    val routes = NotificationRoutes[IO](service)
    val app = routes.`private`.orNotFound

    for {
      // Create test notification
      notification <- service.createNotification(
        CreateNotificationRequest(
          userId = testUser.id,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Test Notification"),
          content = "Test content",
          notificationType = NotificationType.TaskAssigned,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      request <- IO.pure(
        Request[IO](
          Method.POST,
          Uri.unsafeFromString(s"/notifications/${notification.id.value}/read"),
        )
          .withAuthenticatedUser(testUser)
      )

      response <- app.run(request)
      body <- response.as[Map[String, Boolean]]

      // Check unread count after marking as read
      unreadCount <- service.getUnreadCount(testUser.id)

    } yield expect(response.status == Status.Ok) and
      expect(body.get("success").contains(true)) and
      expect(unreadCount == 0L)
  }

  test("POST /notifications/mark-all-read - mark all notifications as read") {
    val service = mockNotificationService
    val routes = NotificationRoutes[IO](service)
    val app = routes.`private`.orNotFound

    for {
      // Create multiple test notifications
      _ <- service.createNotification(
        CreateNotificationRequest(
          userId = testUser.id,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Notification 1"),
          content = "Content 1",
          notificationType = NotificationType.TaskAssigned,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      _ <- service.createNotification(
        CreateNotificationRequest(
          userId = testUser.id,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Notification 2"),
          content = "Content 2",
          notificationType = NotificationType.ProjectUpdate,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      request <- IO.pure(
        Request[IO](Method.POST, uri"/notifications/mark-all-read")
          .withAuthenticatedUser(testUser)
      )

      response <- app.run(request)
      body <- response.as[Map[String, Boolean]]

      // Check unread count after marking all as read
      unreadCount <- service.getUnreadCount(testUser.id)

    } yield expect(response.status == Status.Ok) and
      expect(body.get("success").contains(true)) and
      expect(unreadCount == 0L)
  }

  test("DELETE /notifications/{id} - delete notification") {
    val service = mockNotificationService
    val routes = NotificationRoutes[IO](service)
    val app = routes.`private`.orNotFound

    for {
      // Create test notification
      notification <- service.createNotification(
        CreateNotificationRequest(
          userId = testUser.id,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("To Delete"),
          content = "Will be deleted",
          notificationType = NotificationType.TaskAssigned,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      request <- IO.pure(
        Request[IO](Method.DELETE, Uri.unsafeFromString(s"/notifications/${notification.id.value}"))
          .withAuthenticatedUser(testUser)
      )

      response <- app.run(request)
      body <- response.as[Map[String, Boolean]]

      // Check that notification was deleted
      (notifications, _) <- service.getUserNotifications(testUser.id, NotificationFilters())

    } yield expect(response.status == Status.Ok) and
      expect(body.get("success").contains(true)) and
      expect(notifications.isEmpty)
  }

  test("POST /notifications - create notification") {
    val service = mockNotificationService
    val routes = NotificationRoutes[IO](service)
    val app = routes.`private`.orNotFound

    val createRequest = CreateNotificationRequest(
      userId = testUser.id,
      title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("New Notification"),
      content = "This is a new notification",
      notificationType = NotificationType.SystemAlert,
      priority = NotificationPriority.High,
      deliveryMethods = Set(DeliveryMethod.InApp, DeliveryMethod.Email),
    )

    for {
      request <- IO.pure(
        Request[IO](Method.POST, uri"/notifications")
          .withAuthenticatedUser(testUser)
          .withEntity(createRequest.asJson)
      )

      response <- app.run(request)
      notification <- response.as[Notification]

    } yield expect(response.status == Status.Created) and
      expect(notification.title.value == "New Notification") and
      expect(notification.notificationType == NotificationType.SystemAlert) and
      expect(notification.priority == NotificationPriority.High)
  }

  test("GET /notifications/settings - get notification settings") {
    val service = mockNotificationService
    val routes = NotificationRoutes[IO](service)
    val app = routes.`private`.orNotFound

    for {
      request <- IO.pure(
        Request[IO](Method.GET, uri"/notifications/settings")
          .withAuthenticatedUser(testUser)
      )

      response <- app.run(request)
      settings <- response.as[NotificationSettings]

    } yield expect(response.status == Status.Ok) and
      expect(settings.userId == testUser.id) and
      expect(settings.emailNotifications == true) // Default value
  }

  test("PUT /notifications/settings - update notification settings") {
    val service = mockNotificationService
    val routes = NotificationRoutes[IO](service)
    val app = routes.`private`.orNotFound

    val updateRequest = UpdateNotificationSettingsRequest(
      emailNotifications = Some(false),
      pushNotifications = Some(true),
      taskReminders = Some(false),
      timeZone = Some("Europe/London"),
    )

    for {
      request <- IO.pure(
        Request[IO](Method.PUT, uri"/notifications/settings")
          .withAuthenticatedUser(testUser)
          .withEntity(updateRequest.asJson)
      )

      response <- app.run(request)
      settings <- response.as[NotificationSettings]

    } yield expect(response.status == Status.Ok) and
      expect(settings.emailNotifications == false) and
      expect(settings.pushNotifications == true) and
      expect(settings.taskReminders == false) and
      expect(settings.timeZone == "Europe/London")
  }

  test("GET /notifications/search - search notifications") {
    val service = mockNotificationService
    val routes = NotificationRoutes[IO](service)
    val app = routes.`private`.orNotFound

    for {
      // Create searchable notifications
      _ <- service.createNotification(
        CreateNotificationRequest(
          userId = testUser.id,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Project Update"),
          content = "The marketing project has been updated",
          notificationType = NotificationType.ProjectUpdate,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      _ <- service.createNotification(
        CreateNotificationRequest(
          userId = testUser.id,
          title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Task Assignment"),
          content = "You have been assigned a new task",
          notificationType = NotificationType.TaskAssigned,
          deliveryMethods = Set(DeliveryMethod.InApp),
        )
      )

      request <- IO.pure(
        Request[IO](Method.GET, uri"/notifications/search?query=project")
          .withAuthenticatedUser(testUser)
      )

      response <- app.run(request)
      body <- response.as[Map[String, Any]]

    } yield expect(response.status == Status.Ok) and {
      val results = body.get("results").asInstanceOf[Option[List[Map[String, Any]]]]
      expect(results.isDefined) and
        expect(results.get.length == 1)
    }
  }

  // Helper extension method for authenticated requests
  implicit class RequestOps[F[_]](request: Request[F]) {
    def withAuthenticatedUser(user: AuthedUser): Request[F] =
      // In a real implementation, this would set proper authentication headers/context
      // For testing, we'll simulate the authenticated context
      request
  }
}
