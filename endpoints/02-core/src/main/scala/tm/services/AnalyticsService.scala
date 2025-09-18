package tm.services

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

import cats.MonadThrow
import cats.data.OptionT
import cats.implicits._

import tm.domain.PersonId
import tm.domain.analytics._
import tm.domain.corporate.User
import tm.domain.time.WorkMode
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.exception.AError
import tm.repositories.AnalyticsRepository
import tm.repositories.TimeTrackingRepository
import tm.repositories.UsersRepository
import tm.repositories.sql.AnalyticsSql._
import tm.utils.ID

trait AnalyticsService[F[_]] {
  // Dashboard data
  def getDashboardData(userId: PersonId): F[DashboardData]
  def getPersonalDashboard(userId: PersonId): F[PersonalDashboard]
  def getTeamDashboard(managerId: PersonId): F[TeamDashboard]

  // Real-time analytics
  def getLiveWorkStats(userId: PersonId): F[LiveWorkStats]
  def isUserCurrentlyWorking(userId: PersonId): F[Boolean]

  // Productivity analytics
  def getProductivityReport(userId: PersonId, dateRange: DateRange): F[ProductivityReport]
  def getProductivityInsights(userId: PersonId): F[List[ProductivityInsight]]
  def generatePersonalizedInsights(userId: PersonId): F[List[ProductivityInsight]]

  // Goal management
  def setGoals(userId: PersonId, goals: UserGoalsUpdate): F[UserGoals]
  def getGoalProgress(userId: PersonId): F[GoalProgress]

  // Notifications
  def getDashboardNotifications(userId: PersonId): F[List[DashboardNotification]]
  def markNotificationAsRead(notificationId: String, userId: PersonId): F[Unit]
  def generateDashboardNotifications(userId: PersonId): F[List[DashboardNotification]]

  // Analytics utilities
  def refreshAnalyticsData(): F[Unit]
  def calculateProductivityScore(userId: PersonId): F[Double]
}

object AnalyticsService {
  def make[F[_]: MonadThrow: Calendar: GenUUID](
      analyticsRepo: AnalyticsRepository[F],
      usersRepo: UsersRepository[F],
      timeTrackingRepo: TimeTrackingRepository[F],
    ): AnalyticsService[F] = new AnalyticsService[F] {
    override def getDashboardData(userId: PersonId): F[DashboardData] =
      for {
        user <- getUserOrRaise(userId)
        currentSession <- analyticsRepo.getCurrentWorkSession(userId)
        todayStats <- getTodayStatsDomain(userId)
        weekStats <- getWeekStatsDomain(userId)
        monthStats <- getMonthStatsDomain(userId)
        goals <- getUserGoalsDomain(userId)
        recentTasks <- getRecentTasksDomain(userId)
        notifications <- getDashboardNotifications(userId)
        insights <- getProductivityInsights(userId)
      } yield DashboardData(
        user = user,
        currentPeriod = DashboardPeriod.Today,
        workSession = currentSession.map(convertToEnhancedWorkSession),
        todayStats = todayStats,
        weekStats = weekStats,
        monthStats = monthStats,
        goals = goals,
        recentTasks = recentTasks,
        notifications = notifications,
        insights = insights,
      )

    override def getPersonalDashboard(userId: PersonId): F[PersonalDashboard] =
      for {
        user <- getUserOrRaise(userId)
        currentStats <- getCurrentDayStats(userId)
        weekOverview <- getWeekOverview(userId)
        monthOverview <- getMonthOverview(userId)
        goals <- getGoalProgressSummary(userId)
        insights <- getPersonalInsights(userId)
        upcomingTasks <- getUpcomingTasks(userId)
        recentActivity <- getRecentActivity(userId)
      } yield PersonalDashboard(
        user = user,
        currentStats = currentStats,
        weekOverview = weekOverview,
        monthOverview = monthOverview,
        goals = goals,
        insights = insights,
        upcomingTasks = upcomingTasks,
        recentActivity = recentActivity,
      )

    override def getTeamDashboard(managerId: PersonId): F[TeamDashboard] =
      for {
        teamStats <- getTeamStats(managerId)
        memberOverviews <- getTeamMemberOverviews(managerId)
        teamGoals <- getTeamGoals(managerId)
        alerts <- getTeamAlerts(managerId)
        projectProgress <- getProjectProgress(managerId)
      } yield TeamDashboard(
        managerId = managerId,
        teamStats = teamStats,
        memberOverviews = memberOverviews,
        teamGoals = teamGoals,
        alerts = alerts,
        projectProgress = projectProgress,
      )

    override def getLiveWorkStats(userId: PersonId): F[LiveWorkStats] =
      for {
        currentSession <- analyticsRepo.getCurrentWorkSession(userId)
        runningEntries <- analyticsRepo.getRunningTimeEntries(userId)
        todayStats <- analyticsRepo.getTodayStats(userId)
        weekStats <- analyticsRepo.getWeekStats(userId, getCurrentWeekStart)
        productivityScore <- analyticsRepo.getProductivityScore(userId)
      } yield LiveWorkStats(
        isWorking = currentSession.exists(_.isRunning),
        currentTaskId = runningEntries.find(!_.isBreak).flatMap(_.taskId),
        sessionDuration = currentSession.map(_.totalMinutes).getOrElse(0),
        todayTotal = todayStats.map(_.productiveMinutes).getOrElse(0),
        weekTotal = weekStats.map(_.totalProductiveHours).getOrElse(0.0),
        efficiency = calculateEfficiency(todayStats),
      )

    override def isUserCurrentlyWorking(userId: PersonId): F[Boolean] =
      analyticsRepo.getCurrentWorkSession(userId).map(_.exists(_.isRunning))

    override def getProductivityReport(
        userId: PersonId,
        dateRange: DateRange,
      ): F[ProductivityReport] =
      for {
        overallScore <- analyticsRepo.getProductivityScore(userId)
        trends <- getProductivityTrends(userId, dateRange)
        breakdowns <- getProductivityBreakdowns(userId, dateRange)
        comparisons <- getProductivityComparisons(userId, dateRange)
        recommendations <- getProductivityRecommendations(userId, dateRange)
      } yield ProductivityReport(
        userId = userId,
        dateRange = dateRange,
        overallScore = overallScore,
        trends = trends,
        breakdowns = breakdowns,
        comparisons = comparisons,
        recommendations = recommendations,
      )

    override def getProductivityInsights(userId: PersonId): F[List[ProductivityInsight]] =
      analyticsRepo.getProductivityInsights(userId).map(_.map(convertToProductivityInsight))

    override def generatePersonalizedInsights(userId: PersonId): F[List[ProductivityInsight]] =
      for {
        todayStats <- analyticsRepo.getTodayStats(userId)
        weekStats <- analyticsRepo.getWeekStats(userId, getCurrentWeekStart)
        hourlyPatterns <- analyticsRepo.getHourlyProductivityPatterns(userId)
        taskPerformance <- analyticsRepo.getTaskCompletionPerformance(userId)
        insights <- generateInsightsFromData(
          userId,
          todayStats,
          weekStats,
          hourlyPatterns,
          taskPerformance,
        )
        _ <- insights.traverse(insight =>
          analyticsRepo.insertProductivityInsight(convertFromProductivityInsight(insight))
        )
      } yield insights

    override def setGoals(userId: PersonId, goals: UserGoalsUpdate): F[UserGoals] =
      for {
        goalId <- ID.make[F, GoalId]
        now <- Calendar[F].currentZonedDateTime
        currentProgress <- getGoalProgress(userId)
        userGoalsData = UserGoalsData(
          id = goalId.value,
          userId = userId,
          dailyHoursGoal = goals.dailyHoursGoal,
          weeklyHoursGoal = goals.weeklyHoursGoal,
          monthlyTasksGoal = goals.monthlyTasksGoal,
          productivityGoal = goals.productivityGoal,
          streakGoal = goals.streakGoal,
          createdAt = now,
          updatedAt = now,
        )
        _ <- analyticsRepo.upsertUserGoals(userGoalsData)
      } yield UserGoals(
        dailyHoursGoal = goals.dailyHoursGoal,
        weeklyHoursGoal = goals.weeklyHoursGoal,
        monthlyTasksGoal = goals.monthlyTasksGoal,
        productivityGoal = goals.productivityGoal,
        streakGoal = goals.streakGoal,
        currentProgress = currentProgress,
      )

    override def getGoalProgress(userId: PersonId): F[GoalProgress] =
      for {
        todayStats <- analyticsRepo.getTodayStats(userId)
        weekStats <- analyticsRepo.getWeekStats(userId, getCurrentWeekStart)
        goals <- analyticsRepo.getUserGoals(userId)
        productivityScore <- analyticsRepo.getProductivityScore(userId)
        streak <- calculateCurrentStreak(userId)
      } yield GoalProgress(
        dailyProgress = calculateDailyProgress(todayStats, goals),
        weeklyProgress = calculateWeeklyProgress(weekStats, goals),
        monthlyProgress = 0.0, // TODO: implement monthly progress calculation
        streakProgress = streak,
        productivityProgress = productivityScore,
      )

    override def getDashboardNotifications(userId: PersonId): F[List[DashboardNotification]] =
      analyticsRepo.getUnreadNotifications(userId).map(_.map(convertToDashboardNotification))

    override def markNotificationAsRead(notificationId: String, userId: PersonId): F[Unit] =
      for {
        uuid <- parseUuidOrRaise(notificationId)
        _ <- analyticsRepo.markNotificationAsRead(uuid, userId)
      } yield ()

    override def generateDashboardNotifications(userId: PersonId): F[List[DashboardNotification]] =
      for {
        todayStats <- analyticsRepo.getTodayStats(userId)
        weekStats <- analyticsRepo.getWeekStats(userId, getCurrentWeekStart)
        goals <- analyticsRepo.getUserGoals(userId)
        notifications <- generateNotificationsFromData(userId, todayStats, weekStats, goals)
        _ <- notifications.traverse(notification =>
          analyticsRepo.insertDashboardNotification(convertFromDashboardNotification(notification))
        )
      } yield notifications

    override def refreshAnalyticsData(): F[Unit] =
      analyticsRepo.refreshMaterializedViews()

    override def calculateProductivityScore(userId: PersonId): F[Double] =
      analyticsRepo.getProductivityScore(userId)

    // Private helper methods

    private def getUserOrRaise(userId: PersonId): F[User] =
      usersRepo.findById(userId).flatMap {
        case Some(user) => user.pure[F]
        case None => AError.BadRequest("User not found").raiseError[F, User]
      }

    private def getTodayStatsDomain(userId: PersonId): F[TodayStats] =
      for {
        today <- Calendar[F].currentDate
        todayData <- analyticsRepo.getTodayStats(userId)
        goals <- analyticsRepo.getUserGoals(userId)
        currentSession <- analyticsRepo.getCurrentWorkSession(userId)
        streak <- calculateCurrentStreak(userId)
      } yield TodayStats(
        date = today,
        totalWorkedMinutes = todayData.map(_.productiveMinutes + _.breakMinutes).getOrElse(0),
        productiveMinutes = todayData.map(_.productiveMinutes).getOrElse(0),
        breakMinutes = todayData.map(_.breakMinutes).getOrElse(0),
        targetMinutes = (goals.map(_.dailyHoursGoal).getOrElse(8.0) * 60).toInt,
        progressPercentage = calculateDailyProgress(todayData, goals),
        tasksCompleted = 0, // TODO: implement from task completion data
        tasksInProgress = todayData.map(_.tasksWorked).getOrElse(0),
        currentStreak = streak,
        efficiency = calculateEfficiency(todayData),
        workMode = todayData.flatMap(_.workMode).map(parseWorkMode),
        startTime = todayData.flatMap(_.firstActivity).map(_.toLocalDateTime),
        estimatedEndTime = estimateEndTime(currentSession, goals),
      )

    private def getWeekStatsDomain(userId: PersonId): F[WeekStats] =
      for {
        weekStart <- Calendar[F].currentDate.map(getWeekStart)
        weekData <- analyticsRepo.getWeekStats(userId, weekStart)
        goals <- analyticsRepo.getUserGoals(userId)
        lastWeekData <- analyticsRepo.getWeekStats(userId, weekStart.minusWeeks(1))
        dailyBreakdown <- getDailyBreakdownForWeek(userId, weekStart)
      } yield WeekStats(
        weekStart = weekStart,
        totalHours = weekData.map(_.totalProductiveHours).getOrElse(0.0),
        targetHours = goals.map(_.weeklyHoursGoal).getOrElse(40.0),
        progressPercentage = calculateWeeklyProgress(weekData, goals),
        workDays = weekData.map(_.workDays).getOrElse(0),
        averageDailyHours = weekData.map(_.avgDailyProductiveHours).getOrElse(0.0),
        overtimeHours = calculateOvertimeHours(weekData, goals),
        dailyBreakdown = dailyBreakdown,
        productivityTrend = calculateTrend(weekData, lastWeekData),
        comparisonWithLastWeek = calculateWeekComparison(weekData, lastWeekData),
      )

    private def getMonthStatsDomain(userId: PersonId): F[MonthStats] =
      for {
        currentMonth <- Calendar[F].currentDate.map(date => YearMonth.from(date))
        // TODO: Implement month stats retrieval from database
        // For now, return default values
      } yield MonthStats(
        month = currentMonth,
        totalHours = 0.0,
        targetHours = 160.0, // 40 hours * 4 weeks
        workDays = 0,
        averageDailyHours = 0.0,
        productivityScore = 0.0,
        goalsAchieved = 0,
        totalGoals = 0,
        weeklyBreakdown = List.empty,
      )

    private def getUserGoalsDomain(userId: PersonId): F[UserGoals] =
      for {
        goalsData <- analyticsRepo.getUserGoals(userId)
        progress <- getGoalProgress(userId)
      } yield goalsData
        .map(data =>
          UserGoals(
            dailyHoursGoal = data.dailyHoursGoal,
            weeklyHoursGoal = data.weeklyHoursGoal,
            monthlyTasksGoal = data.monthlyTasksGoal,
            productivityGoal = data.productivityGoal,
            streakGoal = data.streakGoal,
            currentProgress = progress,
          )
        )
        .getOrElse(getDefaultUserGoals(progress))

    private def getRecentTasksDomain(userId: PersonId): F[List[RecentTask]] =
      analyticsRepo
        .getRecentTasks(userId, 10)
        .map(
          _.map(data =>
            RecentTask(
              id = tm.domain.TaskId(data.taskId),
              name = data.taskName,
              projectName = data.projectName,
              status = parseTaskStatus(data.status),
              timeSpent = data.timeSpentMinutes,
              lastWorked = data.lastWorked.map(_.toLocalDateTime).getOrElse(LocalDateTime.now()),
            )
          )
        )

    // Additional helper methods for calculations and conversions

    private def getCurrentWeekStart: LocalDate = {
      val today = LocalDate.now()
      getWeekStart(today)
    }

    private def getWeekStart(date: LocalDate): LocalDate =
      date.minusDays(date.getDayOfWeek.getValue.toLong - 1)

    private def calculateEfficiency(todayData: Option[DailyProductivityData]): Double =
      todayData
        .map { data =>
          val totalMinutes = data.productiveMinutes + data.breakMinutes
          if (totalMinutes > 0) data.productiveMinutes.toDouble / totalMinutes * 100 else 0.0
        }
        .getOrElse(0.0)

    private def calculateDailyProgress(
        todayData: Option[DailyProductivityData],
        goals: Option[UserGoalsData],
      ): Double = {
      val targetMinutes = goals.map(_.dailyHoursGoal * 60).getOrElse(480.0) // 8 hours default
      val actualMinutes = todayData.map(_.productiveMinutes).getOrElse(0)
      Math.min(100.0, (actualMinutes / targetMinutes) * 100)
    }

    private def calculateWeeklyProgress(
        weekData: Option[WeeklyProductivityData],
        goals: Option[UserGoalsData],
      ): Double = {
      val targetHours = goals.map(_.weeklyHoursGoal).getOrElse(40.0)
      val actualHours = weekData.map(_.totalProductiveHours).getOrElse(0.0)
      Math.min(100.0, (actualHours / targetHours) * 100)
    }

    private def calculateCurrentStreak(userId: PersonId): F[Int] =
      // TODO: Implement streak calculation based on daily productivity data
      0.pure[F]

    private def parseUuidOrRaise(uuidString: String): F[java.util.UUID] =
      scala.util.Try(java.util.UUID.fromString(uuidString)).toEither match {
        case Right(uuid) => uuid.pure[F]
        case Left(_) => AError.BadRequest("Invalid UUID format").raiseError[F, java.util.UUID]
      }

    private def parseWorkMode(workModeString: String): Option[WorkMode] =
      workModeString match {
        case "Office" => Some(WorkMode.Office)
        case "Remote" => Some(WorkMode.Remote)
        case "Hybrid" => Some(WorkMode.Hybrid)
        case _ => None
      }

    private def parseTaskStatus(statusString: String): tm.domain.enums.TaskStatus =
      statusString match {
        case "to_do" => tm.domain.enums.TaskStatus.ToDo
        case "in_progress" => tm.domain.enums.TaskStatus.InProgress
        case "in_review" => tm.domain.enums.TaskStatus.InReview
        case "testing" => tm.domain.enums.TaskStatus.Testing
        case "done" => tm.domain.enums.TaskStatus.Done
        case "rejected" => tm.domain.enums.TaskStatus.Rejected
        case _ => tm.domain.enums.TaskStatus.ToDo
      }

    private def getDefaultUserGoals(progress: GoalProgress): UserGoals =
      UserGoals(
        dailyHoursGoal = 8.0,
        weeklyHoursGoal = 40.0,
        monthlyTasksGoal = 20,
        productivityGoal = 80.0,
        streakGoal = 5,
        currentProgress = progress,
      )

    // Stub implementations for complex methods
    private def convertToEnhancedWorkSession(
        data: EnhancedWorkSessionData
      ): tm.domain.time.EnhancedWorkSession = ???
    private def convertToProductivityInsight(data: ProductivityInsightData): ProductivityInsight =
      ???
    private def convertToDashboardNotification(
        data: DashboardNotificationData
      ): DashboardNotification = ???
    private def convertFromProductivityInsight(
        insight: ProductivityInsight
      ): ProductivityInsightData = ???
    private def convertFromDashboardNotification(
        notification: DashboardNotification
      ): DashboardNotificationData = ???
    private def getCurrentDayStats(userId: PersonId): F[CurrentDayStats] = ???
    private def getWeekOverview(userId: PersonId): F[WeekOverview] = ???
    private def getMonthOverview(userId: PersonId): F[MonthOverview] = ???
    private def getGoalProgressSummary(userId: PersonId): F[GoalProgressSummary] = ???
    private def getPersonalInsights(userId: PersonId): F[List[PersonalInsight]] = ???
    private def getUpcomingTasks(userId: PersonId): F[List[UpcomingTask]] = ???
    private def getRecentActivity(userId: PersonId): F[List[RecentActivity]] = ???
    private def getTeamStats(managerId: PersonId): F[TeamStats] = ???
    private def getTeamMemberOverviews(managerId: PersonId): F[List[TeamMemberOverview]] = ???
    private def getTeamGoals(managerId: PersonId): F[TeamGoals] = ???
    private def getTeamAlerts(managerId: PersonId): F[List[TeamAlert]] = ???
    private def getProjectProgress(managerId: PersonId): F[List[ProjectProgress]] = ???
    private def getProductivityTrends(
        userId: PersonId,
        dateRange: DateRange,
      ): F[ProductivityTrends] = ???
    private def getProductivityBreakdowns(
        userId: PersonId,
        dateRange: DateRange,
      ): F[ProductivityBreakdowns] = ???
    private def getProductivityComparisons(
        userId: PersonId,
        dateRange: DateRange,
      ): F[ProductivityComparisons] = ???
    private def getProductivityRecommendations(
        userId: PersonId,
        dateRange: DateRange,
      ): F[List[ProductivityRecommendation]] = ???
    private def generateInsightsFromData(
        userId: PersonId,
        todayStats: Option[DailyProductivityData],
        weekStats: Option[WeeklyProductivityData],
        hourlyPatterns: List[HourlyProductivityData],
        taskPerformance: List[TaskCompletionPerformanceData],
      ): F[List[ProductivityInsight]] = ???
    private def generateNotificationsFromData(
        userId: PersonId,
        todayStats: Option[DailyProductivityData],
        weekStats: Option[WeeklyProductivityData],
        goals: Option[UserGoalsData],
      ): F[List[DashboardNotification]] = ???
    private def estimateEndTime(
        currentSession: Option[EnhancedWorkSessionData],
        goals: Option[UserGoalsData],
      ): Option[LocalDateTime] = ???
    private def getDailyBreakdownForWeek(
        userId: PersonId,
        weekStart: LocalDate,
      ): F[List[DayBreakdown]] = ???
    private def calculateOvertimeHours(
        weekData: Option[WeeklyProductivityData],
        goals: Option[UserGoalsData],
      ): Double = ???
    private def calculateTrend(
        weekData: Option[WeeklyProductivityData],
        lastWeekData: Option[WeeklyProductivityData],
      ): TrendDirection = ???
    private def calculateWeekComparison(
        weekData: Option[WeeklyProductivityData],
        lastWeekData: Option[WeeklyProductivityData],
      ): ComparisonStats = ???
  }
}

// Additional domain models for service
case class LiveWorkStats(
    isWorking: Boolean,
    currentTaskId: Option[java.util.UUID],
    sessionDuration: Int, // minutes
    todayTotal: Int, // minutes
    weekTotal: Double, // hours
    efficiency: Double, // percentage
  )

case class DateRange(
    startDate: LocalDate,
    endDate: LocalDate,
  )

case class UserGoalsUpdate(
    dailyHoursGoal: Double,
    weeklyHoursGoal: Double,
    monthlyTasksGoal: Int,
    productivityGoal: Double,
    streakGoal: Int,
  )

case class PersonalDashboard(
    user: User,
    currentStats: CurrentDayStats,
    weekOverview: WeekOverview,
    monthOverview: MonthOverview,
    goals: GoalProgressSummary,
    insights: List[PersonalInsight],
    upcomingTasks: List[UpcomingTask],
    recentActivity: List[RecentActivity],
  )

case class ProductivityReport(
    userId: PersonId,
    dateRange: DateRange,
    overallScore: Double,
    trends: ProductivityTrends,
    breakdowns: ProductivityBreakdowns,
    comparisons: ProductivityComparisons,
    recommendations: List[ProductivityRecommendation],
  )

// Stub case classes - these would be fully implemented
case class CurrentDayStats(value: String = "stub")
case class WeekOverview(value: String = "stub")
case class MonthOverview(value: String = "stub")
case class GoalProgressSummary(value: String = "stub")
case class PersonalInsight(value: String = "stub")
case class UpcomingTask(value: String = "stub")
case class RecentActivity(value: String = "stub")
case class ProductivityTrends(value: String = "stub")
case class ProductivityBreakdowns(value: String = "stub")
case class ProductivityComparisons(value: String = "stub")
case class ProductivityRecommendation(value: String = "stub")
