package tm.endpoint.routes

import java.time.LocalDate

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import weaver.SimpleIOSuite

import tm.domain.PersonId
import tm.domain.analytics._
import tm.domain.auth.AuthedUser
import tm.domain.corporate.User
import tm.domain.enums.Role
import tm.services.AnalyticsService
import tm.services.DateRange
import tm.services.UserGoalsUpdate

object DashboardRoutesSpec extends SimpleIOSuite {
  def createMockAnalyticsService: AnalyticsService[IO] = new AnalyticsService[IO] {
    def getDashboardData(userId: PersonId): IO[DashboardData] = {
      val mockUser = User(
        id = userId,
        createdAt = java.time.ZonedDateTime.now(),
        role = Role.Employee,
        phone = "+1234567890",
        photo = None,
        corporateId = tm.domain.CorporateId(java.util.UUID.randomUUID()),
        password = None,
        updatedAt = None,
        deletedAt = None,
      )

      IO.pure(
        DashboardData(
          user = mockUser,
          currentPeriod = DashboardPeriod.Today,
          workSession = None,
          todayStats = TodayStats(
            date = LocalDate.now(),
            totalWorkedMinutes = 480,
            productiveMinutes = 400,
            breakMinutes = 80,
            targetMinutes = 480,
            progressPercentage = 83.3,
            tasksCompleted = 3,
            tasksInProgress = 2,
            currentStreak = 5,
            efficiency = 83.3,
            workMode = Some(tm.domain.time.WorkMode.Office),
            startTime = Some(java.time.LocalDateTime.now().minusHours(8)),
            estimatedEndTime = Some(java.time.LocalDateTime.now().plusHours(1)),
          ),
          weekStats = WeekStats(
            weekStart = LocalDate.now().minusDays(3),
            totalHours = 32.0,
            targetHours = 40.0,
            progressPercentage = 80.0,
            workDays = 4,
            averageDailyHours = 8.0,
            overtimeHours = 0.0,
            dailyBreakdown = List.empty,
            productivityTrend = TrendDirection.Up,
            comparisonWithLastWeek =
              ComparisonStats(15.0, TrendDirection.Up, "15% increase from last week"),
          ),
          monthStats = MonthStats(
            month = java.time.YearMonth.now(),
            totalHours = 160.0,
            targetHours = 160.0,
            workDays = 20,
            averageDailyHours = 8.0,
            productivityScore = 85.0,
            goalsAchieved = 3,
            totalGoals = 4,
            weeklyBreakdown = List.empty,
          ),
          goals = UserGoals(
            dailyHoursGoal = 8.0,
            weeklyHoursGoal = 40.0,
            monthlyTasksGoal = 20,
            productivityGoal = 80.0,
            streakGoal = 5,
            currentProgress = GoalProgress(83.3, 80.0, 0.0, 5, 85.0),
          ),
          recentTasks = List.empty,
          notifications = List.empty,
          insights = List.empty,
        )
      )
    }

    def getPersonalDashboard(userId: PersonId): IO[tm.services.PersonalDashboard] =
      IO.raiseError(new NotImplementedError("getPersonalDashboard not implemented in mock"))

    def getTeamDashboard(managerId: PersonId): IO[TeamDashboard] =
      IO.pure(
        TeamDashboard(
          managerId = managerId,
          teamStats = TeamStats(5, 4, 32.0, 160.0, 85.0, 95.0, Some(4.5)),
          memberOverviews = List.empty,
          teamGoals = TeamGoals(200.0, 100, 80.0, TeamGoalProgress(75.0, 80.0, 85.0)),
          alerts = List.empty,
          projectProgress = List.empty,
        )
      )

    def getLiveWorkStats(userId: PersonId): IO[tm.services.LiveWorkStats] =
      IO.pure(
        tm.services
          .LiveWorkStats(
            isWorking = true,
            currentTaskId = Some(java.util.UUID.randomUUID()),
            sessionDuration = 45,
            todayTotal = 380,
            weekTotal = 24.5,
            efficiency = 85.2,
          )
      )

    def isUserCurrentlyWorking(userId: PersonId): IO[Boolean] =
      IO.pure(true)

    def getProductivityReport(
        userId: PersonId,
        dateRange: DateRange,
      ): IO[tm.services.ProductivityReport] =
      IO.pure(
        tm.services
          .ProductivityReport(
            userId = userId,
            dateRange = dateRange,
            overallScore = 85.0,
            trends = tm.services.ProductivityTrends("stub"),
            breakdowns = tm.services.ProductivityBreakdowns("stub"),
            comparisons = tm.services.ProductivityComparisons("stub"),
            recommendations = List.empty,
          )
      )

    def getProductivityInsights(userId: PersonId): IO[List[ProductivityInsight]] =
      IO.pure(
        List(
          ProductivityInsight(
            id = ProductivityInsightId(java.util.UUID.randomUUID()),
            category = InsightCategory.Productivity,
            title = "Peak Performance Hours",
            description = "You are most productive between 10-12 AM",
            actionable = true,
            priority = InsightPriority.Medium,
            metadata = Map("peak_start" -> "10", "peak_end" -> "12"),
            validUntil = Some(java.time.LocalDateTime.now().plusDays(7)),
            createdAt = java.time.LocalDateTime.now(),
          )
        )
      )

    def generatePersonalizedInsights(userId: PersonId): IO[List[ProductivityInsight]] =
      getProductivityInsights(userId)

    def setGoals(userId: PersonId, goals: UserGoalsUpdate): IO[UserGoals] =
      IO.pure(
        UserGoals(
          dailyHoursGoal = goals.dailyHoursGoal,
          weeklyHoursGoal = goals.weeklyHoursGoal,
          monthlyTasksGoal = goals.monthlyTasksGoal,
          productivityGoal = goals.productivityGoal,
          streakGoal = goals.streakGoal,
          currentProgress = GoalProgress(50.0, 60.0, 0.0, 3, 75.0),
        )
      )

    def getGoalProgress(userId: PersonId): IO[GoalProgress] =
      IO.pure(GoalProgress(83.3, 80.0, 75.0, 5, 85.0))

    def getDashboardNotifications(userId: PersonId): IO[List[DashboardNotification]] =
      IO.pure(
        List(
          DashboardNotification(
            id = "notification-1",
            title = "Task Deadline Approaching",
            message = "Task 'Review PR' is due tomorrow",
            `type` = NotificationType.TaskDeadline,
            priority = NotificationPriority.High,
            isRead = false,
            createdAt = java.time.LocalDateTime.now(),
            actionUrl = Some("/tasks/123"),
          )
        )
      )

    def markNotificationAsRead(notificationId: String, userId: PersonId): IO[Unit] =
      IO.unit

    def generateDashboardNotifications(userId: PersonId): IO[List[DashboardNotification]] =
      getDashboardNotifications(userId)

    def refreshAnalyticsData(): IO[Unit] =
      IO.unit

    def calculateProductivityScore(userId: PersonId): IO[Double] =
      IO.pure(85.0)
  }

  def createTestUser: AuthedUser = AuthedUser(
    id = PersonId(java.util.UUID.randomUUID()),
    role = Role.Employee,
  )

  test("GET /dashboard/personal returns dashboard data") {
    val mockService = createMockAnalyticsService
    val routes = DashboardRoutes(mockService).`private`
    val user = createTestUser

    val request = Request[IO](Method.GET, uri"/dashboard/data")
    val response = routes.run(user -> request).value

    response.flatMap {
      case Some(resp) =>
        resp.status match {
          case Status.Ok =>
            resp.as[DashboardData].map { dashboard =>
              expect(dashboard.user.id == user.id) and
                expect(dashboard.todayStats.progressPercentage > 0) and
                expect(dashboard.weekStats.totalHours > 0)
            }
          case other =>
            IO.pure(failure(s"Expected 200 OK, got $other"))
        }
      case None =>
        IO.pure(failure("Route not found"))
    }
  }

  test("GET /dashboard/live returns live work stats") {
    val mockService = createMockAnalyticsService
    val routes = DashboardRoutes(mockService).`private`
    val user = createTestUser

    val request = Request[IO](Method.GET, uri"/dashboard/live")
    val response = routes.run(user -> request).value

    response.flatMap {
      case Some(resp) =>
        resp.status match {
          case Status.Ok =>
            resp.as[tm.services.LiveWorkStats].map { stats =>
              expect(stats.isWorking == true) and
                expect(stats.efficiency > 0) and
                expect(stats.todayTotal >= 0)
            }
          case other =>
            IO.pure(failure(s"Expected 200 OK, got $other"))
        }
      case None =>
        IO.pure(failure("Route not found"))
    }
  }

  test("GET /dashboard/working returns working status") {
    val mockService = createMockAnalyticsService
    val routes = DashboardRoutes(mockService).`private`
    val user = createTestUser

    val request = Request[IO](Method.GET, uri"/dashboard/working")
    val response = routes.run(user -> request).value

    response.flatMap {
      case Some(resp) =>
        resp.status match {
          case Status.Ok =>
            resp.as[Map[String, Boolean]].map { result =>
              expect(result.contains("isWorking")) and
                expect(result("isWorking") == true)
            }
          case other =>
            IO.pure(failure(s"Expected 200 OK, got $other"))
        }
      case None =>
        IO.pure(failure("Route not found"))
    }
  }

  test("GET /dashboard/team returns team dashboard") {
    val mockService = createMockAnalyticsService
    val routes = DashboardRoutes(mockService).`private`
    val user = createTestUser

    val request = Request[IO](Method.GET, uri"/dashboard/team")
    val response = routes.run(user -> request).value

    response.flatMap {
      case Some(resp) =>
        resp.status match {
          case Status.Ok =>
            resp.as[TeamDashboard].map { dashboard =>
              expect(dashboard.managerId == user.id) and
                expect(dashboard.teamStats.totalMembers > 0) and
                expect(dashboard.teamStats.productivity > 0)
            }
          case other =>
            IO.pure(failure(s"Expected 200 OK, got $other"))
        }
      case None =>
        IO.pure(failure("Route not found"))
    }
  }

  test("GET /dashboard/productivity/score returns productivity score") {
    val mockService = createMockAnalyticsService
    val routes = DashboardRoutes(mockService).`private`
    val user = createTestUser

    val request = Request[IO](Method.GET, uri"/dashboard/productivity/score")
    val response = routes.run(user -> request).value

    response.flatMap {
      case Some(resp) =>
        resp.status match {
          case Status.Ok =>
            resp.as[Map[String, Double]].map { result =>
              expect(result.contains("productivityScore")) and
                expect(result("productivityScore") == 85.0)
            }
          case other =>
            IO.pure(failure(s"Expected 200 OK, got $other"))
        }
      case None =>
        IO.pure(failure("Route not found"))
    }
  }

  test("GET /dashboard/insights returns productivity insights") {
    val mockService = createMockAnalyticsService
    val routes = DashboardRoutes(mockService).`private`
    val user = createTestUser

    val request = Request[IO](Method.GET, uri"/dashboard/insights")
    val response = routes.run(user -> request).value

    response.flatMap {
      case Some(resp) =>
        resp.status match {
          case Status.Ok =>
            resp.as[List[ProductivityInsight]].map { insights =>
              expect(insights.nonEmpty) and
                expect(insights.head.title == "Peak Performance Hours") and
                expect(insights.head.category == InsightCategory.Productivity)
            }
          case other =>
            IO.pure(failure(s"Expected 200 OK, got $other"))
        }
      case None =>
        IO.pure(failure("Route not found"))
    }
  }

  test("PUT /dashboard/goals updates user goals") {
    val mockService = createMockAnalyticsService
    val routes = DashboardRoutes(mockService).`private`
    val user = createTestUser

    val goalsUpdate = UserGoalsUpdate(
      dailyHoursGoal = 9.0,
      weeklyHoursGoal = 45.0,
      monthlyTasksGoal = 25,
      productivityGoal = 85.0,
      streakGoal = 7,
    )

    val request = Request[IO](Method.PUT, uri"/dashboard/goals").withEntity(goalsUpdate)
    val response = routes.run(user -> request).value

    response.flatMap {
      case Some(resp) =>
        resp.status match {
          case Status.Ok =>
            resp.as[UserGoals].map { goals =>
              expect(goals.dailyHoursGoal == 9.0) and
                expect(goals.weeklyHoursGoal == 45.0) and
                expect(goals.monthlyTasksGoal == 25)
            }
          case other =>
            IO.pure(failure(s"Expected 200 OK, got $other"))
        }
      case None =>
        IO.pure(failure("Route not found"))
    }
  }

  test("GET /dashboard/notifications returns notifications") {
    val mockService = createMockAnalyticsService
    val routes = DashboardRoutes(mockService).`private`
    val user = createTestUser

    val request = Request[IO](Method.GET, uri"/dashboard/notifications")
    val response = routes.run(user -> request).value

    response.flatMap {
      case Some(resp) =>
        resp.status match {
          case Status.Ok =>
            resp.as[List[DashboardNotification]].map { notifications =>
              expect(notifications.nonEmpty) and
                expect(notifications.head.title == "Task Deadline Approaching") and
                expect(notifications.head.priority == NotificationPriority.High)
            }
          case other =>
            IO.pure(failure(s"Expected 200 OK, got $other"))
        }
      case None =>
        IO.pure(failure("Route not found"))
    }
  }

  test("POST /dashboard/notifications/notification-1/read marks notification as read") {
    val mockService = createMockAnalyticsService
    val routes = DashboardRoutes(mockService).`private`
    val user = createTestUser

    val request = Request[IO](Method.POST, uri"/dashboard/notifications/notification-1/read")
    val response = routes.run(user -> request).value

    response.flatMap {
      case Some(resp) =>
        resp.status match {
          case Status.Ok =>
            resp.as[Map[String, Boolean]].map { result =>
              expect(result.contains("success")) and
                expect(result("success") == true)
            }
          case other =>
            IO.pure(failure(s"Expected 200 OK, got $other"))
        }
      case None =>
        IO.pure(failure("Route not found"))
    }
  }

  test("GET /dashboard/health returns health status") {
    val mockService = createMockAnalyticsService
    val routes = DashboardRoutes(mockService).`private`
    val user = createTestUser

    val request = Request[IO](Method.GET, uri"/dashboard/health")
    val response = routes.run(user -> request).value

    response.flatMap {
      case Some(resp) =>
        resp.status match {
          case Status.Ok =>
            resp.as[Map[String, Any]].attempt.map { result =>
              expect(result.isRight) // Health endpoint should return valid JSON
            }
          case other =>
            IO.pure(failure(s"Expected 200 OK, got $other"))
        }
      case None =>
        IO.pure(failure("Route not found"))
    }
  }
}
