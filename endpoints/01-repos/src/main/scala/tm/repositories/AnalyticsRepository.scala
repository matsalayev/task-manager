package tm.repositories

import java.time.LocalDate

import cats.effect.Resource
import skunk._

import tm.domain.PersonId
import tm.repositories.sql.AnalyticsData
import tm.repositories.sql.AnalyticsSql
import tm.support.skunk.syntax.all._

trait AnalyticsRepository[F[_]] {
  // Dashboard data
  def getTodayStats(userId: PersonId): F[Option[AnalyticsData.DailyProductivityData]]
  def getWeekStats(
      userId: PersonId,
      weekStart: LocalDate,
    ): F[Option[AnalyticsData.WeeklyProductivityData]]
  def getUserGoals(userId: PersonId): F[Option[AnalyticsData.UserGoalsData]]
  def upsertUserGoals(goals: AnalyticsData.UserGoalsData): F[Unit]

  // Recent activity
  def getRecentTasks(userId: PersonId, limit: Int = 10): F[List[AnalyticsData.RecentTaskData]]
  def getUnreadNotifications(userId: PersonId): F[List[AnalyticsData.DashboardNotificationData]]
  def getProductivityInsights(userId: PersonId): F[List[AnalyticsData.ProductivityInsightData]]

  // Work session and time tracking
  def getCurrentWorkSession(userId: PersonId): F[Option[AnalyticsData.EnhancedWorkSessionData]]
  def getRunningTimeEntries(userId: PersonId): F[List[AnalyticsData.TimeEntryData]]
  def getProductivityScore(userId: PersonId): F[Double]

  // Analytics and patterns
  def getUserProductivityRanking(
      userId: PersonId
    ): F[Option[AnalyticsData.UserProductivityRankingData]]
  def getHourlyProductivityPatterns(userId: PersonId): F[List[AnalyticsData.HourlyProductivityData]]
  def getTaskCompletionPerformance(
      userId: PersonId
    ): F[List[AnalyticsData.TaskCompletionPerformanceData]]

  // Team analytics
  def getTeamProductivityOverview(
      userId: PersonId
    ): F[List[AnalyticsData.TeamProductivityOverviewData]]

  // Insights and notifications management
  def insertProductivityInsight(insight: AnalyticsData.ProductivityInsightData): F[Unit]
  def insertDashboardNotification(notification: AnalyticsData.DashboardNotificationData): F[Unit]
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
    override def getTodayStats(userId: PersonId): F[Option[AnalyticsData.DailyProductivityData]] =
      AnalyticsSql.getTodayStats.queryOption(userId)

    override def getWeekStats(
        userId: PersonId,
        weekStart: LocalDate,
      ): F[Option[AnalyticsData.WeeklyProductivityData]] =
      AnalyticsSql.getWeekStats.queryOption((userId, weekStart))

    override def getUserGoals(userId: PersonId): F[Option[AnalyticsData.UserGoalsData]] =
      AnalyticsSql.getUserGoals.queryOption(userId)

    override def upsertUserGoals(goals: AnalyticsData.UserGoalsData): F[Unit] =
      AnalyticsSql.upsertUserGoals.execute(goals)

    override def getRecentTasks(
        userId: PersonId,
        limit: Int,
      ): F[List[AnalyticsData.RecentTaskData]] =
      AnalyticsSql.getRecentTasks.queryList((userId, limit))

    override def getUnreadNotifications(
        userId: PersonId
      ): F[List[AnalyticsData.DashboardNotificationData]] =
      AnalyticsSql.getUnreadNotifications.queryList(userId)

    override def getProductivityInsights(
        userId: PersonId
      ): F[List[AnalyticsData.ProductivityInsightData]] =
      AnalyticsSql.getProductivityInsights.queryList(userId)

    override def getCurrentWorkSession(
        userId: PersonId
      ): F[Option[AnalyticsData.EnhancedWorkSessionData]] =
      AnalyticsSql.getCurrentWorkSession.queryOption(userId)

    override def getRunningTimeEntries(userId: PersonId): F[List[AnalyticsData.TimeEntryData]] =
      AnalyticsSql.getRunningTimeEntries.queryList(userId)

    override def getProductivityScore(userId: PersonId): F[Double] =
      AnalyticsSql.getProductivityScore.queryUnique(userId)

    override def getUserProductivityRanking(
        userId: PersonId
      ): F[Option[AnalyticsData.UserProductivityRankingData]] =
      AnalyticsSql.getUserProductivityRanking.queryOption(userId)

    override def getHourlyProductivityPatterns(
        userId: PersonId
      ): F[List[AnalyticsData.HourlyProductivityData]] =
      AnalyticsSql.getHourlyProductivityPatterns.queryList(userId)

    override def getTaskCompletionPerformance(
        userId: PersonId
      ): F[List[AnalyticsData.TaskCompletionPerformanceData]] =
      AnalyticsSql.getTaskCompletionPerformance.queryList(userId)

    override def getTeamProductivityOverview(
        userId: PersonId
      ): F[List[AnalyticsData.TeamProductivityOverviewData]] =
      AnalyticsSql.getTeamProductivityOverview.queryList(userId)

    override def insertProductivityInsight(
        insight: AnalyticsData.ProductivityInsightData
      ): F[Unit] =
      AnalyticsSql.insertProductivityInsight.execute(insight)

    override def insertDashboardNotification(
        notification: AnalyticsData.DashboardNotificationData
      ): F[Unit] =
      AnalyticsSql.insertDashboardNotification.execute(notification)

    override def markNotificationAsRead(notificationId: java.util.UUID, userId: PersonId): F[Unit] =
      AnalyticsSql.markNotificationAsRead.execute((notificationId, userId))

    override def markInsightAsRead(insightId: java.util.UUID, userId: PersonId): F[Unit] =
      AnalyticsSql.markInsightAsRead.execute((insightId, userId))

    override def refreshMaterializedViews(): F[Unit] =
      AnalyticsSql.refreshMaterializedViews.execute(skunk.Void)
  }
}
