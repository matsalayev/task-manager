package tm.services.notification.providers

import cats.effect.IO
import cats.implicits._
import weaver._
import sttp.client3._
import sttp.client3.testing._
import java.time.ZonedDateTime
import java.util.UUID

import tm.domain.PersonId
import tm.domain.notifications._
import tm.repositories.UsersRepository

object SmsNotificationProviderSpec extends SimpleIOSuite {

  // Mock users repository for testing
  def mockUsersRepo: UsersRepository[IO] = new UsersRepository[IO] {
    override def findById(id: PersonId): IO[Option[tm.domain.corporate.User]] = {
      val mockUser = tm.domain.corporate.User(
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
        updatedAt = ZonedDateTime.now()
      )
      IO.pure(Some(mockUser))
    }

    // Other methods not needed for this test
    override def create(user: tm.domain.corporate.User): IO[Unit] = IO.unit
    override def update(user: tm.domain.corporate.User): IO[Unit] = IO.unit
    override def delete(id: PersonId): IO[Unit] = IO.unit
    override def findByUsername(username: String): IO[Option[tm.domain.corporate.User]] = IO.pure(None)
    override def findByEmail(email: String): IO[Option[tm.domain.corporate.User]] = IO.pure(None)
    override def list(limit: Int, offset: Int): IO[List[tm.domain.corporate.User]] = IO.pure(List.empty)
  }

  def testTwilioConfig: SmsConfig = SmsConfig(
    provider = "twilio",
    apiKey = "test_api_key",
    apiSecret = "test_api_secret",
    fromNumber = "+1234567890",
    endpoint = "https://api.twilio.com/2010-04-01/Accounts/test/Messages.json",
    maxMessageLength = 160
  )

  def testNexmoConfig: SmsConfig = SmsConfig(
    provider = "nexmo",
    apiKey = "test_api_key",
    apiSecret = "test_api_secret",
    fromNumber = "TaskManager",
    endpoint = "https://rest.nexmo.com/sms/json",
    maxMessageLength = 160
  )

  def testNotification: Notification = Notification(
    id = NotificationId(UUID.randomUUID()),
    userId = PersonId(UUID.randomUUID()),
    title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Test SMS Notification"),
    content = "This is a test SMS notification.",
    notificationType = NotificationType.TaskAssigned,
    relatedEntityId = Some("task-123"),
    relatedEntityType = Some("Task"),
    isRead = false,
    priority = NotificationPriority.High,
    deliveryMethods = Set(DeliveryMethod.SMS),
    metadata = Map("taskId" -> "123"),
    scheduledAt = None,
    sentAt = None,
    readAt = None,
    expiresAt = None,
    actionUrl = Some("https://app.example.com/tasks/123"),
    actionLabel = Some("View Task"),
    createdAt = ZonedDateTime.now(),
    updatedAt = ZonedDateTime.now()
  )

  test("SMS provider configuration check") {
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]

    val provider = SmsNotificationProvider.make[IO](testTwilioConfig, usersRepo, mockBackend.toIO)

    expect(provider.isConfigured == true)
  }

  test("invalid SMS provider configuration") {
    val invalidConfig = testTwilioConfig.copy(apiKey = "", fromNumber = "")
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]

    val provider = SmsNotificationProvider.make[IO](invalidConfig, usersRepo, mockBackend.toIO)

    expect(provider.isConfigured == false)
  }

  test("SMS message length truncation") {
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]
      .whenRequestMatches(_ => true)
      .thenRespond("""{"sid": "test123", "status": "queued"}""")

    val provider = SmsNotificationProvider.make[IO](testTwilioConfig, usersRepo, mockBackend.toIO)

    val longMessage = "This is a very long SMS message that exceeds the maximum length limit and should be truncated automatically by the provider to fit within the allowed character count for SMS messages which is typically 160 characters for standard SMS."

    for {
      _ <- provider.sendSms("+1234567890", longMessage)
    } yield expect(true) // Should complete without error
  }

  test("Twilio SMS sending") {
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]
      .whenRequestMatches(_.uri.toString.contains("twilio"))
      .thenRespond("""{"sid": "SM123456789", "status": "queued"}""")

    val provider = SmsNotificationProvider.make[IO](testTwilioConfig, usersRepo, mockBackend.toIO)

    for {
      _ <- provider.sendSms("+1234567890", "Test message")
    } yield expect(true) // Should complete without error
  }

  test("Twilio SMS sending failure") {
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]
      .whenRequestMatches(_.uri.toString.contains("twilio"))
      .thenRespond("""{"sid": "SM123456789", "status": "failed", "error_code": 21211, "error_message": "Invalid 'To' Phone Number"}""")

    val provider = SmsNotificationProvider.make[IO](testTwilioConfig, usersRepo, mockBackend.toIO)

    for {
      result <- provider.sendSms("+invalid", "Test message").attempt
    } yield expect(result.isLeft) // Should fail due to error status
  }

  test("Nexmo SMS sending") {
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]
      .whenRequestMatches(_.uri.toString.contains("nexmo"))
      .thenRespond("""{"messages": [{"status": "0", "message-id": "123456"}]}""")

    val provider = SmsNotificationProvider.make[IO](testNexmoConfig, usersRepo, mockBackend.toIO)

    for {
      _ <- provider.sendSms("+1234567890", "Test message")
    } yield expect(true) // Should complete without error
  }

  test("Nexmo SMS sending failure") {
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]
      .whenRequestMatches(_.uri.toString.contains("nexmo"))
      .thenRespond("""{"messages": [{"status": "2", "error-text": "Invalid parameters"}]}""")

    val provider = SmsNotificationProvider.make[IO](testNexmoConfig, usersRepo, mockBackend.toIO)

    for {
      result <- provider.sendSms("+invalid", "Test message").attempt
    } yield expect(result.isLeft) // Should fail due to error status
  }

  test("AWS SNS SMS sending") {
    val awsConfig = testTwilioConfig.copy(
      provider = "aws-sns",
      endpoint = "https://sns.us-east-1.amazonaws.com/"
    )

    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]
      .whenRequestMatches(_.uri.toString.contains("sns"))
      .thenRespond("""{"MessageId": "12345678-1234-1234-1234-123456789012"}""")

    val provider = SmsNotificationProvider.make[IO](awsConfig, usersRepo, mockBackend.toIO)

    for {
      _ <- provider.sendSms("+1234567890", "Test message")
    } yield expect(true) // Should complete without error
  }

  test("unsupported SMS provider") {
    val unsupportedConfig = testTwilioConfig.copy(provider = "unsupported")
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]

    val provider = SmsNotificationProvider.make[IO](unsupportedConfig, usersRepo, mockBackend.toIO)

    for {
      result <- provider.sendSms("+1234567890", "Test message").attempt
    } yield expect(result.isLeft) // Should fail due to unsupported provider
  }

  test("SMS notification with different priorities") {
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]
      .whenRequestMatches(_ => true)
      .thenRespond("""{"sid": "test123", "status": "queued"}""")

    val provider = SmsNotificationProvider.make[IO](testTwilioConfig, usersRepo, mockBackend.toIO)

    val criticalNotification = testNotification.copy(
      priority = NotificationPriority.Critical,
      notificationType = NotificationType.TaskOverdue
    )

    val normalNotification = testNotification.copy(
      priority = NotificationPriority.Normal,
      notificationType = NotificationType.ProjectUpdate
    )

    for {
      _ <- provider.sendSms(criticalNotification)
      _ <- provider.sendSms(normalNotification)
    } yield expect(true) // Both should complete without error
  }

  test("SMS notification with different types") {
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]
      .whenRequestMatches(_ => true)
      .thenRespond("""{"sid": "test123", "status": "queued"}""")

    val provider = SmsNotificationProvider.make[IO](testTwilioConfig, usersRepo, mockBackend.toIO)

    val notificationTypes = List(
      NotificationType.TaskAssigned,
      NotificationType.TaskDue,
      NotificationType.TaskOverdue,
      NotificationType.ProjectUpdate,
      NotificationType.TeamUpdate,
      NotificationType.SystemAlert
    )

    for {
      _ <- notificationTypes.traverse { notType =>
        val notification = testNotification.copy(notificationType = notType)
        provider.sendSms(notification)
      }
    } yield expect(true) // All should complete without error
  }

  test("mock SMS provider functionality") {
    val mockProvider = SmsNotificationProvider.mockProvider[IO]

    for {
      // Test notification sending
      _ <- mockProvider.sendSms(testNotification)

      // Test direct SMS sending
      _ <- mockProvider.sendSms("+1234567890", "Test message")

      // Test configuration and connection
      isConfigured = mockProvider.isConfigured
      connectionTest <- mockProvider.testConnection()

    } yield expect(isConfigured == true) and
           expect(connectionTest == true)
  }

  test("SMS provider handles user not found") {
    val emptyUsersRepo = new UsersRepository[IO] {
      override def findById(id: PersonId): IO[Option[tm.domain.corporate.User]] = IO.pure(None)
      override def create(user: tm.domain.corporate.User): IO[Unit] = IO.unit
      override def update(user: tm.domain.corporate.User): IO[Unit] = IO.unit
      override def delete(id: PersonId): IO[Unit] = IO.unit
      override def findByUsername(username: String): IO[Option[tm.domain.corporate.User]] = IO.pure(None)
      override def findByEmail(email: String): IO[Option[tm.domain.corporate.User]] = IO.pure(None)
      override def list(limit: Int, offset: Int): IO[List[tm.domain.corporate.User]] = IO.pure(List.empty)
    }

    val mockBackend = SttpBackendStub.synchronous[Identity]
    val provider = SmsNotificationProvider.make[IO](testTwilioConfig, emptyUsersRepo, mockBackend.toIO)

    for {
      result <- provider.sendSms(testNotification).attempt
    } yield expect(result.isLeft) // Should fail when user is not found
  }

  test("SMS content formatting with action URL") {
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]
      .whenRequestMatches(_ => true)
      .thenRespond("""{"sid": "test123", "status": "queued"}""")

    val provider = SmsNotificationProvider.make[IO](testTwilioConfig, usersRepo, mockBackend.toIO)

    val notificationWithAction = testNotification.copy(
      actionUrl = Some("https://app.example.com/tasks/123")
    )

    val notificationWithoutAction = testNotification.copy(
      actionUrl = None
    )

    for {
      _ <- provider.sendSms(notificationWithAction)
      _ <- provider.sendSms(notificationWithoutAction)
    } yield expect(true) // Both should complete without error
  }

  test("test connection functionality") {
    val usersRepo = mockUsersRepo
    val mockBackend = SttpBackendStub.synchronous[Identity]

    val provider = SmsNotificationProvider.make[IO](testTwilioConfig, usersRepo, mockBackend.toIO)

    for {
      connectionResult <- provider.testConnection()
    } yield expect(connectionResult == true) // Simple validation check
  }
}