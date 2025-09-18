package tm.repositories

import java.time.LocalDate
import java.time.LocalDateTime

import cats.effect.Resource
import cats.implicits._
import skunk._

import tm.domain.PersonId
import tm.domain.analytics._
import tm.repositories.sql.AnalyticsSql._
import tm.support.skunk.syntax.all._

trait AnalyticsRepository[F[_]] {
  // Dashboard data
  def getTodayStats(userId: PersonId): F[Option[DailyProductivityData]]
  def getWeekStats(userId: PersonId, weekStart: LocalDate): F[Option[WeeklyProductivityData]]
  def getUserGoals(userId: PersonId): F[Option[UserGoalsData]]
  def upsertUserGoals(goals: UserGoalsData): F[Unit]

  // Recent activity
  def getRecentTasks(userId: PersonId, limit: Int = 10): F[List[RecentTaskData]]
  def getUnreadNotifications(userId: PersonId): F[List[DashboardNotificationData]]
  def getProductivityInsights(userId: PersonId): F[List[ProductivityInsightData]]

  // Work session and time tracking
  def getCurrentWorkSession(userId: PersonId): F[Option[EnhancedWorkSessionData]]
  def getRunningTimeEntries(userId: PersonId): F[List[TimeEntryData]]
  def getProductivityScore(userId: PersonId): F[Double]

  // Analytics and patterns
  def getUserProductivityRanking(userId: PersonId): F[Option[UserProductivityRankingData]]
  def getHourlyProductivityPatterns(userId: PersonId): F[List[HourlyProductivityData]]
  def getTaskCompletionPerformance(userId: PersonId): F[List[TaskCompletionPerformanceData]]

  // Team analytics
  def getTeamProductivityOverview(userId: PersonId): F[List[TeamProductivityOverviewData]]

  // Insights and notifications management
  def insertProductivityInsight(insight: ProductivityInsightData): F[Unit]
  def insertDashboardNotification(notification: DashboardNotificationData): F[Unit]
  def markNotificationAsRead(notificationId: java.util.UUID, userId: PersonId): F[Unit]
  def markInsightAsRead(insightId: java.util.UUID, userId: PersonId): F[Unit]

  // Maintenance
  def refreshMaterializedViews(): F[Unit]
}

object AnalyticsRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): AnalyticsRepository[F] = new AnalyticsRepository[F] {
    override def getTodayStats(userId: PersonId): F[Option[DailyProductivityData]] =
      AnalyticsSql.getTodayStats.queryOption(userId)

    override def getWeekStats(
        userId: PersonId,
        weekStart: LocalDate,
      ): F[Option[WeeklyProductivityData]] =
      AnalyticsSql.getWeekStats.queryOption((userId, weekStart))

    override def getUserGoals(userId: PersonId): F[Option[UserGoalsData]] =
      AnalyticsSql.getUserGoals.queryOption(userId)

    override def upsertUserGoals(goals: UserGoalsData): F[Unit] =
      AnalyticsSql.upsertUserGoals.execute(goals)

    override def getRecentTasks(userId: PersonId, limit: Int): F[List[RecentTaskData]] =
      AnalyticsSql.getRecentTasks.queryList((userId, limit)).map { results =>
        results.map {
          case (taskId, taskName, projectName, status, timeSpent, lastWorked) =>
            RecentTaskData(taskId, taskName, projectName, status, timeSpent, lastWorked)
        }
      }

    override def getUnreadNotifications(userId: PersonId): F[List[DashboardNotificationData]] =
      AnalyticsSql.getUnreadNotifications.queryList(userId)

    override def getProductivityInsights(userId: PersonId): F[List[ProductivityInsightData]] =
      AnalyticsSql.getProductivityInsights.queryList(userId)

    override def getCurrentWorkSession(userId: PersonId): F[Option[EnhancedWorkSessionData]] =
      AnalyticsSql
        .getCurrentWorkSession
        .queryOption(userId)
        .map(_.map {
          case (
                 id,
                 userId,
                 startTime,
                 endTime,
                 workMode,
                 isRunning,
                 totalMinutes,
                 breakMinutes,
                 productiveMinutes,
                 description,
                 location,
                 createdAt,
               ) =>
            EnhancedWorkSessionData(
              id,
              userId,
              startTime,
              endTime,
              workMode,
              isRunning,
              totalMinutes,
              breakMinutes,
              productiveMinutes,
              description,
              location,
              createdAt,
            )
        })

    override def getRunningTimeEntries(userId: PersonId): F[List[TimeEntryData]] =
      AnalyticsSql.getRunningTimeEntries.queryList(userId).map { results =>
        results.map {
          case (
                 id,
                 userId,
                 taskId,
                 workSessionId,
                 startTime,
                 endTime,
                 duration,
                 description,
                 isRunning,
                 isBreak,
                 breakReason,
                 isManual,
                 createdAt,
                 updatedAt,
               ) =>
            TimeEntryData(
              id,
              userId,
              taskId,
              workSessionId,
              startTime,
              endTime,
              duration,
              description,
              isRunning,
              isBreak,
              breakReason,
              isManual,
              createdAt,
              updatedAt,
            )
        }
      }

    override def getProductivityScore(userId: PersonId): F[Double] =
      AnalyticsSql.getProductivityScore.queryUnique(userId)

    override def getUserProductivityRanking(
        userId: PersonId
      ): F[Option[UserProductivityRankingData]] =
      AnalyticsSql
        .getUserProductivityRanking
        .queryOption(userId)
        .map(_.map {
          case (
                 userId,
                 fullName,
                 role,
                 corporateId,
                 todayProductiveMinutes,
                 todayTasks,
                 todayBreakMinutes,
                 weekProductiveHours,
                 weekTasks,
                 weekEfficiency,
                 dailyRank,
                 weeklyRank,
                 currentStatus,
               ) =>
            UserProductivityRankingData(
              userId,
              fullName,
              role,
              corporateId,
              todayProductiveMinutes,
              todayTasks,
              todayBreakMinutes,
              weekProductiveHours,
              weekTasks,
              weekEfficiency,
              dailyRank,
              weeklyRank,
              currentStatus,
            )
        })

    override def getHourlyProductivityPatterns(userId: PersonId): F[List[HourlyProductivityData]] =
      AnalyticsSql.getHourlyProductivityPatterns.queryList(userId).map { results =>
        results.map {
          case (
                 userId,
                 hourOfDay,
                 dayOfWeek,
                 sessionCount,
                 avgDurationMinutes,
                 totalDurationMinutes,
                 avgProductiveDuration,
               ) =>
            HourlyProductivityData(
              userId,
              hourOfDay,
              dayOfWeek,
              sessionCount,
              avgDurationMinutes,
              totalDurationMinutes,
              avgProductiveDuration,
            )
        }
      }

    override def getTaskCompletionPerformance(
        userId: PersonId
      ): F[List[TaskCompletionPerformanceData]] =
      AnalyticsSql.getTaskCompletionPerformance.queryList(userId).map { results =>
        results.map {
          case (
                 taskId,
                 taskName,
                 projectId,
                 status,
                 createdAt,
                 finishedAt,
                 deadline,
                 estimatedHours,
                 actualHoursSpent,
                 usersWorked,
                 timeEntriesCount,
                 timeAccuracyPercentage,
                 deadlineVarianceDays,
                 lastWorkedOn,
               ) =>
            TaskCompletionPerformanceData(
              taskId,
              taskName,
              projectId,
              status,
              createdAt,
              finishedAt,
              deadline,
              estimatedHours,
              actualHoursSpent,
              usersWorked,
              timeEntriesCount,
              timeAccuracyPercentage,
              deadlineVarianceDays,
              lastWorkedOn,
            )
        }
      }

    override def getTeamProductivityOverview(
        userId: PersonId
      ): F[List[TeamProductivityOverviewData]] =
      AnalyticsSql.getTeamProductivityOverview.queryList(userId).map { results =>
        results.map {
          case (
                 projectId,
                 projectName,
                 corporateId,
                 teamSize,
                 todayTeamHours,
                 todayAvgMemberHours,
                 activeToday,
                 weekTeamHours,
                 weekAvgMemberHours,
                 weekAvgEfficiency,
                 completedTasks,
                 inProgressTasks,
                 totalTasks,
               ) =>
            TeamProductivityOverviewData(
              projectId,
              projectName,
              corporateId,
              teamSize,
              todayTeamHours,
              todayAvgMemberHours,
              activeToday,
              weekTeamHours,
              weekAvgMemberHours,
              weekAvgEfficiency,
              completedTasks,
              inProgressTasks,
              totalTasks,
            )
        }
      }

    override def insertProductivityInsight(insight: ProductivityInsightData): F[Unit] =
      AnalyticsSql.insertProductivityInsight.execute(insight)

    override def insertDashboardNotification(notification: DashboardNotificationData): F[Unit] =
      AnalyticsSql.insertDashboardNotification.execute(notification)

    override def markNotificationAsRead(notificationId: java.util.UUID, userId: PersonId): F[Unit] =
      AnalyticsSql.markNotificationAsRead.execute((notificationId, userId))

    override def markInsightAsRead(insightId: java.util.UUID, userId: PersonId): F[Unit] =
      AnalyticsSql.markInsightAsRead.execute((insightId, userId))

    override def refreshMaterializedViews(): F[Unit] =
      AnalyticsSql.refreshMaterializedViews.execute(skunk.Void)
  }
}
