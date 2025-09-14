# Dashboard Analytics Backend Implementation

## UI Requirements Analysis

### Mavjud UI Funksiyalar:
- Work time counters with start/stop/pause
- Work mode selection (office/remote)
- Weekly work hours chart
- Toast notification system
- Task list with status filtering
- Basic time statistics

### Qo'shilish Kerak:
- Real-time productivity metrics
- Team comparison dashboards
- Advanced analytics charts
- Goal tracking and progress
- Automated insights and recommendations

## Implementation Tasks

### 1. Dashboard Domain Models
**Priority: 🔴 High**
**Fayl**: `endpoints/00-domain/src/main/scala/tm/domain/analytics/`

```scala
case class DashboardData(
  user: User,
  currentPeriod: DashboardPeriod,
  workSession: Option[ActiveWorkSession],
  todayStats: TodayStats,
  weekStats: WeekStats,
  monthStats: MonthStats,
  goals: UserGoals,
  recentTasks: List[RecentTask],
  notifications: List[DashboardNotification],
  insights: List[ProductivityInsight]
)

case class TodayStats(
  date: LocalDate,
  totalWorkedMinutes: Int,
  productiveMinutes: Int,
  breakMinutes: Int,
  targetMinutes: Int,
  progressPercentage: Double,
  tasksCompleted: Int,
  tasksInProgress: Int,
  currentStreak: Int, // consecutive work days
  efficiency: Double, // productive time / total time
  workMode: Option[WorkMode],
  startTime: Option[LocalDateTime],
  estimatedEndTime: Option[LocalDateTime]
)

case class WeekStats(
  weekStart: LocalDate,
  totalHours: Double,
  targetHours: Double,
  progressPercentage: Double,
  workDays: Int,
  averageDailyHours: Double,
  overtimeHours: Double,
  dailyBreakdown: List[DayBreakdown],
  productivityTrend: TrendDirection,
  comparisonWithLastWeek: ComparisonStats
)

case class MonthStats(
  month: YearMonth,
  totalHours: Double,
  targetHours: Double,
  workDays: Int,
  averageDailyHours: Double,
  productivityScore: Double,
  goalsAchieved: Int,
  totalGoals: Int,
  weeklyBreakdown: List[WeekBreakdown]
)

case class ActiveWorkSession(
  id: WorkSessionId,
  startTime: LocalDateTime,
  currentDuration: Int, // minutes
  workMode: WorkMode,
  currentTask: Option[TaskInfo],
  breaksSinceStart: Int,
  productiveTime: Int,
  isOnBreak: Boolean,
  breakStartTime: Option[LocalDateTime]
)

case class UserGoals(
  dailyHoursGoal: Double,
  weeklyHoursGoal: Double,
  monthlyTasksGoal: Int,
  productivityGoal: Double, // percentage
  streakGoal: Int, // consecutive work days
  currentProgress: GoalProgress
)

case class ProductivityInsight(
  id: String,
  category: InsightCategory,
  title: String,
  description: String,
  actionable: Boolean,
  priority: InsightPriority,
  metadata: Map[String, String],
  validUntil: Option[LocalDateTime]
)

sealed trait InsightCategory
object InsightCategory {
  case object Productivity extends InsightCategory
  case object TimeManagement extends InsightCategory
  case object WorkLifeBalance extends InsightCategory
  case object Goals extends InsightCategory
  case object Team extends InsightCategory
}

sealed trait TrendDirection
object TrendDirection {
  case object Up extends TrendDirection
  case object Down extends TrendDirection
  case object Stable extends TrendDirection
}

case class TeamDashboard(
  managerId: UserId,
  teamStats: TeamStats,
  memberOverviews: List[TeamMemberOverview],
  teamGoals: TeamGoals,
  alerts: List[TeamAlert],
  projectProgress: List[ProjectProgress]
)

case class TeamStats(
  totalMembers: Int,
  activeMembers: Int,
  todayHours: Double,
  weekHours: Double,
  productivity: Double,
  onTimeDelivery: Double,
  memberSatisfaction: Option[Double]
)

case class ExecutiveDashboard(
  companyStats: CompanyStats,
  departmentStats: List[DepartmentStats],
  keyMetrics: List[KPIMetric],
  trends: List[TrendAnalysis],
  alerts: List[ExecutiveAlert],
  upcomingDeadlines: List[DeadlineAlert]
)
```

### 2. Analytics Repository
**Fayl**: `endpoints/01-repos/src/main/scala/tm/repos/AnalyticsRepo.scala`

```scala
trait AnalyticsRepo[F[_]] {
  // Dashboard data
  def getDashboardData(userId: UserId, date: LocalDate): F[DashboardData]
  def getTodayStats(userId: UserId): F[TodayStats]
  def getWeekStats(userId: UserId, weekStart: LocalDate): F[WeekStats]
  def getMonthStats(userId: UserId, month: YearMonth): F[MonthStats]

  // Real-time data
  def getCurrentWorkSession(userId: UserId): F[Option[ActiveWorkSession]]
  def getUserLiveStats(userId: UserId): F[LiveStats]

  // Productivity analytics
  def getProductivityTrends(userId: UserId, dateRange: DateRange): F[List[ProductivityDataPoint]]
  def getHourlyProductivity(userId: UserId, date: LocalDate): F[List[HourlyProductivity]]
  def getTaskProductivity(userId: UserId, dateRange: DateRange): F[List[TaskProductivityStat]]

  // Goal tracking
  def getUserGoals(userId: UserId): F[UserGoals]
  def updateUserGoals(userId: UserId, goals: UserGoalsUpdate): F[UserGoals]
  def getGoalProgress(userId: UserId): F[GoalProgress]

  // Insights and recommendations
  def generateInsights(userId: UserId): F[List[ProductivityInsight]]
  def getPersonalizedRecommendations(userId: UserId): F[List[Recommendation]]

  // Team analytics (for managers)
  def getTeamDashboard(managerId: UserId, teamIds: List[UserId]): F[TeamDashboard]
  def getTeamProductivity(managerId: UserId, teamIds: List[UserId], dateRange: DateRange): F[TeamProductivityReport]
  def getTeamWorkload(managerId: UserId, teamIds: List[UserId]): F[List[TeamMemberWorkload]]

  // Executive dashboard (for directors)
  def getExecutiveDashboard(companyId: CompanyId): F[ExecutiveDashboard]
  def getCompanyKPIs(companyId: CompanyId, dateRange: DateRange): F[List[KPIMetric]]
  def getDepartmentComparison(companyId: CompanyId, dateRange: DateRange): F[List[DepartmentComparison]]

  // Time series data for charts
  def getTimeSeriesData(query: TimeSeriesQuery): F[List[TimeSeriesDataPoint]]
  def getAggregatedData(query: AggregationQuery): F[AggregatedResult]

  // Alerts and notifications
  def getUserAlerts(userId: UserId): F[List[DashboardAlert]]
  def getTeamAlerts(managerId: UserId): F[List[TeamAlert]]
  def markAlertAsRead(alertId: AlertId, userId: UserId): F[Unit]
}

case class TimeSeriesQuery(
  userId: Option[UserId],
  companyId: Option[CompanyId],
  metric: MetricType,
  granularity: TimeGranularity, // Hour, Day, Week, Month
  dateRange: DateRange,
  groupBy: Option[GroupByField],
  filters: Map[String, String]
)

case class ProductivityDataPoint(
  timestamp: LocalDateTime,
  value: Double,
  metadata: Map[String, String]
)

case class LiveStats(
  isWorking: Boolean,
  currentTaskId: Option[TaskId],
  sessionDuration: Int, // minutes
  todayTotal: Int,
  weekTotal: Double,
  efficiency: Double
)
```

### 3. Analytics Service
**Fayl**: `endpoints/02-core/src/main/scala/tm/services/AnalyticsService.scala`

```scala
trait AnalyticsService[F[_]] {
  // Dashboard data
  def getDashboardData(userId: UserId, period: DashboardPeriod = DashboardPeriod.Today): F[Either[AnalyticsError, DashboardData]]
  def getPersonalDashboard(userId: UserId): F[Either[AnalyticsError, PersonalDashboard]]
  def getTeamDashboard(managerId: UserId): F[Either[AnalyticsError, TeamDashboard]]
  def getExecutiveDashboard(userId: UserId): F[Either[AnalyticsError, ExecutiveDashboard]]

  // Real-time analytics
  def getLiveWorkStats(userId: UserId): F[LiveWorkStats]
  def getWorkingNowCount(companyId: CompanyId): F[Int]
  def getActiveSessionsCount(companyId: CompanyId): F[Int]

  // Productivity analytics
  def getProductivityReport(userId: UserId, dateRange: DateRange): F[Either[AnalyticsError, ProductivityReport]]
  def getProductivityComparison(userId: UserId, comparePeriod: DateRange, basePeriod: DateRange): F[ProductivityComparison]
  def getPersonalizedInsights(userId: UserId): F[List[ProductivityInsight]]

  // Goal management
  def setGoals(userId: UserId, goals: UserGoalsUpdate): F[Either[AnalyticsError, UserGoals]]
  def getGoalProgress(userId: UserId): F[GoalProgressReport]
  def trackGoalAchievement(userId: UserId, goalType: GoalType, value: Double): F[Unit]

  // Time analytics
  def getTimeDistribution(userId: UserId, dateRange: DateRange): F[TimeDistributionReport]
  def getWorkPatterns(userId: UserId, dateRange: DateRange): F[WorkPatternsAnalysis]
  def getEfficiencyTrends(userId: UserId, dateRange: DateRange): F[EfficiencyTrendsReport]

  // Team analytics
  def getTeamProductivityOverview(managerId: UserId): F[Either[AnalyticsError, TeamProductivityOverview]]
  def getTeamTimeComparison(managerId: UserId, dateRange: DateRange): F[TeamTimeComparison]
  def getTeamGoalsProgress(managerId: UserId): F[TeamGoalsProgress]

  // Advanced analytics
  def getPredictiveAnalytics(userId: UserId): F[PredictiveAnalytics]
  def getAnomalyDetection(userId: UserId, dateRange: DateRange): F[List[Anomaly]]
  def getWorkLoadBalancing(teamId: TeamId): F[WorkLoadBalance]

  // Export and reporting
  def generateAnalyticsReport(userId: UserId, reportConfig: ReportConfig): F[Either[AnalyticsError, GeneratedReport]]
  def scheduleRecurringReport(userId: UserId, reportConfig: RecurringReportConfig): F[Either[AnalyticsError, ScheduledReport]]

  // Notifications
  def generateDashboardNotifications(userId: UserId): F[List[DashboardNotification]]
  def getPersonalizedAlerts(userId: UserId): F[List[PersonalizedAlert]]
}

sealed trait AnalyticsError
object AnalyticsError {
  case object InsufficientData extends AnalyticsError
  case object AccessDenied extends AnalyticsError
  case object InvalidDateRange extends AnalyticsError
  case class CalculationError(message: String) extends AnalyticsError
}

case class PersonalDashboard(
  user: User,
  currentStats: CurrentDayStats,
  weekOverview: WeekOverview,
  monthOverview: MonthOverview,
  goals: GoalProgressSummary,
  insights: List[PersonalInsight],
  upcomingTasks: List[UpcomingTask],
  recentActivity: List[RecentActivity]
)

case class ProductivityReport(
  userId: UserId,
  dateRange: DateRange,
  overallScore: Double,
  trends: ProductivityTrends,
  breakdowns: ProductivityBreakdowns,
  comparisons: ProductivityComparisons,
  recommendations: List[ProductivityRecommendation]
)

case class PredictiveAnalytics(
  burnoutRisk: BurnoutRisk,
  productivityForecast: ProductivityForecast,
  goalAchievementProbability: Map[GoalType, Double],
  optimalWorkSchedule: OptimalScheduleRecommendation,
  taskCompletionPrediction: List[TaskCompletionPrediction]
)

case class BurnoutRisk(
  riskLevel: RiskLevel, // Low, Medium, High
  score: Double, // 0-100
  factors: List[BurnoutFactor],
  recommendations: List[BurnoutPrevention]
)
```

### 4. Dashboard API Routes
**Fayl**: `endpoints/03-api/src/main/scala/tm/endpoint/routes/DashboardRoutes.scala`

```scala
object DashboardRoutes {
  def routes[F[_]: Async](
    analyticsService: AnalyticsService[F],
    authMiddleware: AuthMiddleware[F]
  ): HttpRoutes[F] = {

    val protectedRoutes: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of[AuthedUser, F] {
      // Personal dashboard
      case GET -> Root / "personal" as user =>
        // Get personal dashboard data

      case GET -> Root / "personal" / "live" as user =>
        // Get real-time personal stats

      case GET -> Root / "today" as user =>
        // Today's statistics

      case GET -> Root / "week" :? WeekStartQueryParam(weekStart) as user =>
        // Week statistics

      case GET -> Root / "month" :? MonthQueryParam(month) as user =>
        // Month statistics

      // Analytics endpoints
      case GET -> Root / "productivity" :? DateRangeQueryParam(range) as user =>
        // Productivity analytics

      case GET -> Root / "trends" :? MetricQueryParam(metric) +& DateRangeQueryParam(range) as user =>
        // Trend analysis

      case GET -> Root / "insights" as user =>
        // Personal insights and recommendations

      case GET -> Root / "time-distribution" :? DateRangeQueryParam(range) as user =>
        // Time distribution analytics

      // Goals management
      case GET -> Root / "goals" as user =>
        // Get user goals and progress

      case PUT -> Root / "goals" as user =>
        // Update user goals

      case GET -> Root / "goals" / "progress" as user =>
        // Detailed goal progress

      // Team dashboard (for managers)
      case GET -> Root / "team" as user =>
        // Team dashboard overview

      case GET -> Root / "team" / "productivity" :? DateRangeQueryParam(range) as user =>
        // Team productivity analytics

      case GET -> Root / "team" / "workload" as user =>
        // Team workload distribution

      case GET -> Root / "team" / "live" as user =>
        // Real-time team activity

      // Executive dashboard (for directors)
      case GET -> Root / "executive" as user =>
        // Executive dashboard

      case GET -> Root / "company" / "kpis" :? DateRangeQueryParam(range) as user =>
        // Company KPIs

      case GET -> Root / "company" / "departments" as user =>
        // Department comparison

      // Charts data
      case GET -> Root / "charts" / "time-series" :? TimeSeriesQueryParams as user =>
        // Time series data for charts

      case GET -> Root / "charts" / "productivity-heatmap" :? DateRangeQueryParam(range) as user =>
        // Productivity heatmap data

      case GET -> Root / "charts" / "work-patterns" :? DateRangeQueryParam(range) as user =>
        // Work patterns visualization data

      // Notifications and alerts
      case GET -> Root / "notifications" as user =>
        // Dashboard notifications

      case POST -> Root / "notifications" / UUIDVar(notificationId) / "read" as user =>
        // Mark notification as read

      case GET -> Root / "alerts" as user =>
        // Personal alerts

      // Export functionality
      case POST -> Root / "export" / "report" as user =>
        // Generate custom report

      case GET -> Root / "export" / "dashboard" / "pdf" :? DateRangeQueryParam(range) as user =>
        // Export dashboard as PDF
    }

    authMiddleware(protectedRoutes)
  }
}
```

### 5. WebSocket for Real-time Updates

```scala
// Real-time dashboard updates via WebSocket
object DashboardWebSocket {
  def routes[F[_]: Async](
    analyticsService: AnalyticsService[F],
    authMiddleware: AuthMiddleware[F]
  ): HttpRoutes[F] = {

    val protectedRoutes: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of[AuthedUser, F] {
      case GET -> Root / "ws" / "dashboard" as user =>
        // WebSocket connection for real-time dashboard updates
        // Sends updates every 30 seconds or on significant events

      case GET -> Root / "ws" / "team" as user =>
        // WebSocket for real-time team activity updates
    }

    authMiddleware(protectedRoutes)
  }
}
```

### 6. Database Views for Analytics
**Migration**: `endpoints/01-repos/src/main/resources/db/migration/V007__analytics_views.sql`

```sql
-- Daily productivity view
CREATE MATERIALIZED VIEW daily_productivity_stats AS
SELECT
    user_id,
    DATE(start_time) as report_date,
    COUNT(CASE WHEN NOT is_break THEN 1 END) as productive_entries,
    SUM(CASE WHEN NOT is_break THEN duration_minutes ELSE 0 END) as productive_minutes,
    SUM(CASE WHEN is_break THEN duration_minutes ELSE 0 END) as break_minutes,
    COUNT(DISTINCT task_id) FILTER (WHERE task_id IS NOT NULL) as tasks_worked,
    AVG(CASE WHEN NOT is_break AND duration_minutes > 0 THEN duration_minutes END) as avg_session_length,
    MIN(start_time) as first_activity,
    MAX(COALESCE(end_time, start_time)) as last_activity
FROM time_entries
WHERE start_time >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY user_id, DATE(start_time);

-- Weekly productivity aggregation
CREATE MATERIALIZED VIEW weekly_productivity_stats AS
SELECT
    user_id,
    DATE_TRUNC('week', report_date) as week_start,
    SUM(productive_minutes) as total_productive_minutes,
    SUM(break_minutes) as total_break_minutes,
    COUNT(DISTINCT report_date) as work_days,
    AVG(productive_minutes) as avg_daily_productive,
    SUM(tasks_worked) as total_tasks_worked,
    AVG(avg_session_length) as avg_session_length
FROM daily_productivity_stats
WHERE report_date >= CURRENT_DATE - INTERVAL '12 weeks'
GROUP BY user_id, DATE_TRUNC('week', report_date);

-- User productivity ranking view
CREATE VIEW user_productivity_ranking AS
SELECT
    u.id as user_id,
    u.first_name,
    u.last_name,
    COALESCE(dps.productive_minutes, 0) as today_productive_minutes,
    COALESCE(wps.total_productive_minutes, 0) as week_productive_minutes,
    COALESCE(dps.tasks_worked, 0) as today_tasks,
    RANK() OVER (ORDER BY COALESCE(dps.productive_minutes, 0) DESC) as daily_rank,
    RANK() OVER (ORDER BY COALESCE(wps.total_productive_minutes, 0) DESC) as weekly_rank
FROM users u
LEFT JOIN daily_productivity_stats dps ON u.id = dps.user_id AND dps.report_date = CURRENT_DATE
LEFT JOIN weekly_productivity_stats wps ON u.id = wps.user_id AND wps.week_start = DATE_TRUNC('week', CURRENT_DATE);

-- Team productivity overview
CREATE VIEW team_productivity_overview AS
SELECT
    pm.project_id,
    p.name as project_name,
    COUNT(DISTINCT pm.user_id) as team_size,
    SUM(COALESCE(dps.productive_minutes, 0)) as today_team_minutes,
    AVG(COALESCE(dps.productive_minutes, 0)) as avg_member_minutes,
    COUNT(DISTINCT CASE WHEN dps.productive_minutes > 0 THEN pm.user_id END) as active_today
FROM project_members pm
JOIN projects p ON pm.project_id = p.id
LEFT JOIN daily_productivity_stats dps ON pm.user_id = dps.user_id AND dps.report_date = CURRENT_DATE
GROUP BY pm.project_id, p.name;

-- Refresh materialized views daily
CREATE OR REPLACE FUNCTION refresh_productivity_stats()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY daily_productivity_stats;
    REFRESH MATERIALIZED VIEW CONCURRENTLY weekly_productivity_stats;
END;
$$ LANGUAGE plpgsql;

-- Schedule daily refresh (requires pg_cron extension)
-- SELECT cron.schedule('refresh-productivity-stats', '0 1 * * *', 'SELECT refresh_productivity_stats();');
```

### 7. API Documentation Examples

#### GET /api/dashboard/personal
```json
{
  "user": {
    "id": "uuid",
    "firstName": "John",
    "lastName": "Doe"
  },
  "currentStats": {
    "isWorking": true,
    "currentTaskId": "uuid",
    "sessionDuration": 125,
    "todayTotal": 380,
    "weekTotal": 24.5,
    "efficiency": 85.2
  },
  "todayStats": {
    "date": "2024-01-15",
    "totalWorkedMinutes": 380,
    "productiveMinutes": 324,
    "breakMinutes": 56,
    "targetMinutes": 480,
    "progressPercentage": 79.2,
    "tasksCompleted": 3,
    "tasksInProgress": 2,
    "currentStreak": 5,
    "efficiency": 85.2
  },
  "weekStats": {
    "weekStart": "2024-01-15",
    "totalHours": 24.5,
    "targetHours": 40,
    "progressPercentage": 61.25,
    "workDays": 3,
    "averageDailyHours": 8.17,
    "overtimeHours": 1.5
  },
  "goals": {
    "dailyHoursGoal": 8,
    "weeklyHoursGoal": 40,
    "productivityGoal": 80,
    "currentProgress": {
      "dailyProgress": 79.2,
      "weeklyProgress": 61.25,
      "productivityProgress": 85.2
    }
  },
  "insights": [
    {
      "category": "Productivity",
      "title": "Peak Performance",
      "description": "You're most productive between 10-12 AM",
      "actionable": true,
      "priority": "Medium"
    }
  ]
}
```

## Testing Strategy

1. **Unit Tests**: Analytics calculations va aggregations
2. **Performance Tests**: Dashboard load times with large datasets
3. **Real-time Tests**: WebSocket updates va live data
4. **Integration Tests**: Cross-module data consistency

## Estimated Time: 2-3 hafta