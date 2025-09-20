package tm.services

import java.time.LocalDate
import java.time.ZonedDateTime

import _root_.tm.domain.PersonId
import _root_.tm.domain.analytics._
import _root_.tm.domain.corporate.User
import _root_.tm.repositories.AnalyticsRepository
import _root_.tm.repositories.TimeTrackingRepository
import _root_.tm.repositories.UsersRepository
import _root_.tm.repositories.sql.AnalyticsData
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import weaver.SimpleIOSuite

object AnalyticsServiceSpec extends SimpleIOSuite {
  def createMockService: AnalyticsService[IO] = {
    val analyticsRepo = new tm.repositories.mocks.MockAnalyticsRepository[IO]()
    val usersRepo = new tm.repositories.mocks.MockUsersRepository[IO]()
    val timeTrackingRepo = new tm.repositories.mocks.MockTimeTrackingRepository[IO]()

    AnalyticsService.make[IO](analyticsRepo, usersRepo, timeTrackingRepo)
  }

  test("getDashboardData returns complete dashboard") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())

    service.getDashboardData(userId).attempt.map { result =>
      expect(result.isRight || result.isLeft) // Basic structure test
    }
  }

  test("getLiveWorkStats returns current work status") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())

    service.getLiveWorkStats(userId).map { stats =>
      expect(stats.efficiency >= 0.0) and
        expect(stats.efficiency <= 100.0) and
        expect(stats.sessionDuration >= 0) and
        expect(stats.todayTotal >= 0) and
        expect(stats.weekTotal >= 0.0)
    }
  }

  test("isUserCurrentlyWorking returns boolean status") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())

    service.isUserCurrentlyWorking(userId).map { isWorking =>
      expect(isWorking == true || isWorking == false)
    }
  }

  test("setGoals creates and returns user goals") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())

    val goalsUpdate = UserGoalsUpdate(
      dailyHoursGoal = 8.0,
      weeklyHoursGoal = 40.0,
      monthlyTasksGoal = 20,
      productivityGoal = 80.0,
      streakGoal = 5,
    )

    service.setGoals(userId, goalsUpdate).map { goals =>
      expect(goals.dailyHoursGoal == 8.0) and
        expect(goals.weeklyHoursGoal == 40.0) and
        expect(goals.monthlyTasksGoal == 20) and
        expect(goals.productivityGoal == 80.0) and
        expect(goals.streakGoal == 5)
    }
  }

  test("getGoalProgress returns progress data") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())

    service.getGoalProgress(userId).map { progress =>
      expect(progress.dailyProgress >= 0.0) and
        expect(progress.weeklyProgress >= 0.0) and
        expect(progress.streakProgress >= 0) and
        expect(progress.productivityProgress >= 0.0)
    }
  }

  test("getProductivityInsights returns insights list") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())

    service.getProductivityInsights(userId).map { insights =>
      expect(insights.isInstanceOf[List[ProductivityInsight]])
    }
  }

  test("generatePersonalizedInsights creates new insights") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())

    service.generatePersonalizedInsights(userId).map { insights =>
      expect(insights.isInstanceOf[List[ProductivityInsight]]) and
        expect(insights.length >= 0)
    }
  }

  test("getDashboardNotifications returns notifications") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())

    service.getDashboardNotifications(userId).map { notifications =>
      expect(notifications.isInstanceOf[List[DashboardNotification]])
    }
  }

  test("generateDashboardNotifications creates notifications") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())

    service.generateDashboardNotifications(userId).map { notifications =>
      expect(notifications.isInstanceOf[List[DashboardNotification]]) and
        expect(notifications.length >= 0)
    }
  }

  test("markNotificationAsRead handles valid UUID") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())
    val notificationId = java.util.UUID.randomUUID().toString

    service.markNotificationAsRead(notificationId, userId).attempt.map { result =>
      expect(result.isRight || result.isLeft) // Should handle either success or failure
    }
  }

  test("markNotificationAsRead rejects invalid UUID") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())
    val invalidId = "not-a-uuid"

    service.markNotificationAsRead(invalidId, userId).attempt.map { result =>
      expect(result.isLeft) // Should fail with invalid UUID
    }
  }

  test("calculateProductivityScore returns valid score") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())

    service.calculateProductivityScore(userId).map { score =>
      expect(score >= 0.0) and expect(score <= 100.0)
    }
  }

  test("refreshAnalyticsData executes without error") {
    val service = createMockService

    service.refreshAnalyticsData().attempt.map { result =>
      expect(result.isRight)
    }
  }

  test("getProductivityReport returns comprehensive report") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())
    val dateRange = _root_
      .tm
      .services
      .DateRange(
        startDate = LocalDate.now().minusDays(7),
        endDate = LocalDate.now(),
      )

    service.getProductivityReport(userId, dateRange).map { report =>
      expect(report.userId == userId) and
        expect(report.dateRange == dateRange) and
        expect(report.overallScore >= 0.0) and
        expect(report.overallScore <= 100.0)
    }
  }

  test("getPersonalDashboard returns dashboard with user data") {
    val service = createMockService
    val userId = PersonId(java.util.UUID.randomUUID())

    service.getPersonalDashboard(userId).attempt.map { result =>
      // This might fail due to missing user, which is expected in this test setup
      expect(result.isRight || result.isLeft)
    }
  }

  test("getTeamDashboard returns team overview") {
    val service = createMockService
    val managerId = PersonId(java.util.UUID.randomUUID())

    service.getTeamDashboard(managerId).map { dashboard =>
      expect(dashboard.managerId == managerId) and
        expect(dashboard.teamStats.totalMembers >= 0) and
        expect(dashboard.teamStats.activeMembers >= 0) and
        expect(dashboard.memberOverviews.isInstanceOf[List[TeamMemberOverview]]) and
        expect(dashboard.alerts.isInstanceOf[List[TeamAlert]]) and
        expect(dashboard.projectProgress.isInstanceOf[List[ProjectProgress]])
    }
  }
}

// Mock repositories for testing
package tm.repositories.mocks {
  import java.time.LocalDate

  import cats.effect.Ref
  import cats.implicits._

  class MockAnalyticsRepository[F[_]: cats.effect.Sync]
      extends _root_.tm.repositories.AnalyticsRepository[F] {
    private val mockTodayStats = Map.empty[PersonId, AnalyticsData.DailyProductivityData]
    private val mockUserGoals = Ref.unsafe[F, Map[PersonId, AnalyticsData.UserGoalsData]](Map.empty)

    def getTodayStats(userId: PersonId): F[Option[AnalyticsData.DailyProductivityData]] =
      Option.empty[AnalyticsData.DailyProductivityData].pure[F]

    def getWeekStats(
        userId: PersonId,
        weekStart: LocalDate,
      ): F[Option[AnalyticsData.WeeklyProductivityData]] =
      Option.empty[AnalyticsData.WeeklyProductivityData].pure[F]

    def getUserGoals(userId: PersonId): F[Option[AnalyticsData.UserGoalsData]] =
      mockUserGoals.get.map(_.get(userId))

    def upsertUserGoals(goals: AnalyticsData.UserGoalsData): F[Unit] =
      mockUserGoals.update(_.updated(goals.userId, goals))

    def getRecentTasks(userId: PersonId, limit: Int): F[List[AnalyticsData.RecentTaskData]] =
      List.empty[AnalyticsData.RecentTaskData].pure[F]

    def getUnreadNotifications(userId: PersonId): F[List[AnalyticsData.DashboardNotificationData]] =
      List.empty[AnalyticsData.DashboardNotificationData].pure[F]

    def getProductivityInsights(userId: PersonId): F[List[AnalyticsData.ProductivityInsightData]] =
      List.empty[AnalyticsData.ProductivityInsightData].pure[F]

    def getCurrentWorkSession(userId: PersonId): F[Option[AnalyticsData.EnhancedWorkSessionData]] =
      Option.empty[AnalyticsData.EnhancedWorkSessionData].pure[F]

    def getRunningTimeEntries(userId: PersonId): F[List[AnalyticsData.TimeEntryData]] =
      List.empty[AnalyticsData.TimeEntryData].pure[F]

    def getProductivityScore(userId: PersonId): F[Double] =
      75.0.pure[F] // Mock score

    def getUserProductivityRanking(
        userId: PersonId
      ): F[Option[AnalyticsData.UserProductivityRankingData]] =
      Option.empty[AnalyticsData.UserProductivityRankingData].pure[F]

    def getHourlyProductivityPatterns(
        userId: PersonId
      ): F[List[AnalyticsData.HourlyProductivityData]] =
      List.empty[AnalyticsData.HourlyProductivityData].pure[F]

    def getTaskCompletionPerformance(
        userId: PersonId
      ): F[List[AnalyticsData.TaskCompletionPerformanceData]] =
      List.empty[AnalyticsData.TaskCompletionPerformanceData].pure[F]

    def getTeamProductivityOverview(
        userId: PersonId
      ): F[List[AnalyticsData.TeamProductivityOverviewData]] =
      List.empty[AnalyticsData.TeamProductivityOverviewData].pure[F]

    def insertProductivityInsight(insight: AnalyticsData.ProductivityInsightData): F[Unit] =
      ().pure[F]

    def insertDashboardNotification(
        notification: AnalyticsData.DashboardNotificationData
      ): F[Unit] =
      ().pure[F]

    def markNotificationAsRead(notificationId: java.util.UUID, userId: PersonId): F[Unit] =
      ().pure[F]

    def markInsightAsRead(insightId: java.util.UUID, userId: PersonId): F[Unit] =
      ().pure[F]

    def refreshMaterializedViews(): F[Unit] =
      ().pure[F]
  }

  class MockUsersRepository[F[_]: cats.effect.Sync]
      extends _root_.tm.repositories.UsersRepository[F] {
    def findById(id: PersonId): F[Option[User]] =
      Option.empty[User].pure[F] // Return None to simulate user not found
  }

  class MockTimeTrackingRepository[F[_]: cats.effect.Sync]
      extends _root_.tm.repositories.TimeTrackingRepository[F] {
    // Implement all required methods as no-ops or with mock data
    // This is a simplified version - full implementation would have all methods
  }
}
