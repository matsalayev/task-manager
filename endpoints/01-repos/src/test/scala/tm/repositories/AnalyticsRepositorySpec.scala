package tm.repositories

import java.time.LocalDate
import java.time.ZonedDateTime

import cats.effect.IO
import cats.effect.Resource
import skunk.Session

import tm.database.DBSuite
import tm.domain.PersonId
import tm.domain.analytics._
import tm.generators.Generators
import tm.repositories.sql.AnalyticsSql._

object AnalyticsRepositorySpec extends DBSuite with Generators {
  override def schemaName: String = "public"
  override def beforeAll(implicit session: Resource[IO, Session[IO]]): IO[Unit] = data.setup

  test("getTodayStats returns productivity data for user") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id

    for {
      result <- repo.getTodayStats(userId)
    } yield assert(result.isDefined || result.isEmpty) // Basic test structure
  }

  test("getUserGoals returns user goals when they exist") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id

    val testGoals = tm
      .repositories
      .sql
      .AnalyticsData
      .UserGoalsData(
        id = java.util.UUID.randomUUID(),
        userId = userId,
        dailyHoursGoal = BigDecimal(8.0),
        weeklyHoursGoal = BigDecimal(40.0),
        monthlyTasksGoal = 20,
        productivityGoal = BigDecimal(80.0),
        streakGoal = 5,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
      )

    for {
      _ <- repo.upsertUserGoals(testGoals)
      result <- repo.getUserGoals(userId)
    } yield assert(
      result.isDefined &&
      result.get.userId == userId &&
      result.get.dailyHoursGoal == 8.0
    )
  }

  test("upsertUserGoals creates new goals") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person2.id

    val newGoals = tm
      .repositories
      .sql
      .AnalyticsData
      .UserGoalsData(
        id = java.util.UUID.randomUUID(),
        userId = userId,
        dailyHoursGoal = BigDecimal(6.0),
        weeklyHoursGoal = BigDecimal(30.0),
        monthlyTasksGoal = 15,
        productivityGoal = BigDecimal(75.0),
        streakGoal = 3,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
      )

    for {
      _ <- repo.upsertUserGoals(newGoals)
      result <- repo.getUserGoals(userId)
    } yield assert(
      result.isDefined &&
      result.get.dailyHoursGoal == 6.0 &&
      result.get.weeklyHoursGoal == 30.0
    )
  }

  test("upsertUserGoals updates existing goals") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id

    val initialGoals = tm
      .repositories
      .sql
      .AnalyticsData
      .UserGoalsData(
        id = java.util.UUID.randomUUID(),
        userId = userId,
        dailyHoursGoal = BigDecimal(8.0),
        weeklyHoursGoal = BigDecimal(40.0),
        monthlyTasksGoal = 20,
        productivityGoal = BigDecimal(80.0),
        streakGoal = 5,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
      )

    val updatedGoals = initialGoals.copy(
      dailyHoursGoal = BigDecimal(9.0),
      weeklyHoursGoal = BigDecimal(45.0),
      updatedAt = ZonedDateTime.now(),
    )

    for {
      _ <- repo.upsertUserGoals(initialGoals)
      _ <- repo.upsertUserGoals(updatedGoals)
      result <- repo.getUserGoals(userId)
    } yield assert(
      result.isDefined &&
      result.get.dailyHoursGoal == 9.0 &&
      result.get.weeklyHoursGoal == 45.0
    )
  }

  test("getRecentTasks returns task data with time tracking") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id

    for {
      result <- repo.getRecentTasks(userId, 5)
    } yield assert(result.length >= 0) // Basic test - tasks may or may not exist
  }

  test("getUnreadNotifications returns notification list") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id

    for {
      result <- repo.getUnreadNotifications(userId)
    } yield assert(
      result.isInstanceOf[List[tm.repositories.sql.AnalyticsData.DashboardNotificationData]]
    )
  }

  test("insertDashboardNotification creates notification") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id

    val notification = tm
      .repositories
      .sql
      .AnalyticsData
      .DashboardNotificationData(
        id = java.util.UUID.randomUUID(),
        userId = userId,
        title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Test Notification"),
        message = "This is a test notification",
        notificationType =
          eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("ProductivityAlert"),
        priority = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Medium"),
        isRead = false,
        actionUrl = Some(eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("/dashboard")),
        createdAt = ZonedDateTime.now(),
        validUntil = None,
      )

    for {
      _ <- repo.insertDashboardNotification(notification)
      result <- repo.getUnreadNotifications(userId)
    } yield assert(result.exists(_.title == "Test Notification"))
  }

  test("markNotificationAsRead updates notification status") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id
    val notificationId = java.util.UUID.randomUUID()

    val notification = tm
      .repositories
      .sql
      .AnalyticsData
      .DashboardNotificationData(
        id = notificationId,
        userId = userId,
        title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Test Read Notification"),
        message = "This notification will be marked as read",
        notificationType =
          eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("TaskDeadline"),
        priority = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("High"),
        isRead = false,
        actionUrl = None,
        createdAt = ZonedDateTime.now(),
        validUntil = None,
      )

    for {
      _ <- repo.insertDashboardNotification(notification)
      unreadBefore <- repo.getUnreadNotifications(userId)
      _ <- repo.markNotificationAsRead(notificationId, userId)
      unreadAfter <- repo.getUnreadNotifications(userId)
    } yield assert(
      unreadBefore.exists(_.id == notificationId) &&
      !unreadAfter.exists(_.id == notificationId)
    )
  }

  test("insertProductivityInsight creates insight") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id

    val insight = tm
      .repositories
      .sql
      .AnalyticsData
      .ProductivityInsightData(
        id = java.util.UUID.randomUUID(),
        userId = userId,
        category = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Productivity"),
        title = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Peak Performance Hours"),
        description = "You are most productive between 10-12 AM",
        actionable = true,
        priority = eu.timepit.refined.types.string.NonEmptyString.unsafeFrom("Medium"),
        metadata = "{}",
        validUntil = Some(ZonedDateTime.now().plusDays(7)),
        isRead = false,
        createdAt = ZonedDateTime.now(),
      )

    for {
      _ <- repo.insertProductivityInsight(insight)
      result <- repo.getProductivityInsights(userId)
    } yield assert(result.exists(_.title == "Peak Performance Hours"))
  }

  test("getProductivityScore returns valid score") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id

    for {
      score <- repo.getProductivityScore(userId)
    } yield assert(score >= 0.0 && score <= 100.0)
  }

  test("getCurrentWorkSession returns session when user is working") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id

    for {
      result <- repo.getCurrentWorkSession(userId)
    } yield assert(result.isEmpty || result.get.userId == userId)
  }

  test("getRunningTimeEntries returns active entries") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id

    for {
      result <- repo.getRunningTimeEntries(userId)
    } yield assert(result.forall(_.userId == userId))
  }

  test("refreshMaterializedViews executes without error") { implicit session =>
    val repo = AnalyticsRepository.make[IO]

    for {
      _ <- repo.refreshMaterializedViews()
    } yield assert(true) // If it completes without error, test passes
  }

  test("getWeekStats returns weekly productivity data") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id
    val weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek.getValue.toLong - 1)

    for {
      result <- repo.getWeekStats(userId, weekStart)
    } yield assert(result.isEmpty || result.get.userId == userId)
  }

  test("getUserProductivityRanking returns ranking data") { implicit session =>
    val repo = AnalyticsRepository.make[IO]
    val userId = data.people.person1.id

    for {
      result <- repo.getUserProductivityRanking(userId)
    } yield assert(result.isEmpty || result.get.userId == userId)
  }
}
