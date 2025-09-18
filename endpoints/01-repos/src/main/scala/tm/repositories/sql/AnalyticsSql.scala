package tm.repositories.sql

import java.time.LocalDate
import java.time.LocalDateTime

import skunk._
import skunk.codec.all._
import skunk.implicits._

import tm.domain.PersonId
import tm.domain.analytics._
import tm.support.skunk.Sql
import tm.support.skunk.codecs._
import tm.support.skunk.syntax.all.skunkSyntaxFragmentOps

private[repositories] object AnalyticsSql extends Sql[PersonId] {

  // Daily Productivity Stats Codec
  val dailyProductivityStatsCodec: Codec[DailyProductivityData] =
    (id *: date *: int4 *: int4 *: int4 *: int4 *: int4 *: zonedDateTime.opt *: zonedDateTime.opt *: numeric.opt *: nes.opt)
      .to[DailyProductivityData]

  // Weekly Productivity Stats Codec
  val weeklyProductivityStatsCodec: Codec[WeeklyProductivityData] =
    (id *: date *: numeric *: numeric *: numeric *: int4 *: numeric *: numeric *: int4 *: numeric *: numeric *: int4)
      .to[WeeklyProductivityData]

  // User Goals Codec
  val userGoalsCodec: Codec[UserGoalsData] =
    (uuid *: id *: numeric *: numeric *: int4 *: numeric *: int4 *: zonedDateTime *: zonedDateTime)
      .to[UserGoalsData]

  // Productivity Insights Codec
  val productivityInsightCodec: Codec[ProductivityInsightData] =
    (uuid *: id *: nes *: nes *: text *: bool *: nes *: jsonb *: zonedDateTime.opt *: bool *: zonedDateTime)
      .to[ProductivityInsightData]

  // Dashboard Notification Codec
  val dashboardNotificationCodec: Codec[DashboardNotificationData] =
    (uuid *: id *: nes *: text *: nes *: nes *: bool *: nes.opt *: zonedDateTime *: zonedDateTime.opt)
      .to[DashboardNotificationData]

  // Get today's stats for a user
  val getTodayStats: Query[PersonId, DailyProductivityData] =
    sql"""
      SELECT user_id, report_date, productive_minutes, break_minutes, productive_sessions,
             break_sessions, tasks_worked, first_activity, last_activity, avg_session_length, work_mode
      FROM daily_productivity_stats
      WHERE user_id = $id AND report_date = CURRENT_DATE
      LIMIT 1
    """.query(dailyProductivityStatsCodec)

  // Get week stats for a user
  val getWeekStats: Query[(PersonId, LocalDate), WeeklyProductivityData] =
    sql"""
      SELECT user_id, week_start, total_productive_hours, total_break_hours, total_hours,
             work_days, avg_daily_productive_hours, avg_daily_total_hours, total_tasks_worked,
             avg_daily_tasks, efficiency_percentage, total_sessions
      FROM weekly_productivity_stats
      WHERE user_id = ${id} AND week_start = ${date}
      LIMIT 1
    """.query(weeklyProductivityStatsCodec)

  // Get user goals
  val getUserGoals: Query[PersonId, UserGoalsData] =
    sql"""
      SELECT id, user_id, daily_hours_goal, weekly_hours_goal, monthly_tasks_goal,
             productivity_goal, streak_goal, created_at, updated_at
      FROM user_goals
      WHERE user_id = $id
      LIMIT 1
    """.query(userGoalsCodec)

  // Upsert user goals
  val upsertUserGoals: Command[UserGoalsData] =
    sql"""
      INSERT INTO user_goals (id, user_id, daily_hours_goal, weekly_hours_goal, monthly_tasks_goal,
                             productivity_goal, streak_goal, created_at, updated_at)
      VALUES ($userGoalsCodec)
      ON CONFLICT (user_id)
      DO UPDATE SET
        daily_hours_goal = EXCLUDED.daily_hours_goal,
        weekly_hours_goal = EXCLUDED.weekly_hours_goal,
        monthly_tasks_goal = EXCLUDED.monthly_tasks_goal,
        productivity_goal = EXCLUDED.productivity_goal,
        streak_goal = EXCLUDED.streak_goal,
        updated_at = EXCLUDED.updated_at
    """.command

  // Get recent tasks for user
  val getRecentTasks: Query[(PersonId, Int), RecentTaskData] =
    sql"""
      SELECT DISTINCT t.id, t.name, p.name as project_name, t.status,
             COALESCE(SUM(te.duration), 0) as time_spent_minutes,
             MAX(te.start_time) as last_worked
      FROM tasks t
      JOIN projects p ON t.project_id = p.id
      JOIN assignees a ON t.id = a.task_id
      LEFT JOIN time_entries te ON t.id = te.task_id AND NOT te.is_break
      WHERE a.user_id = ${id}
        AND (te.start_time >= CURRENT_DATE - INTERVAL '7 days' OR t.status IN ('in_progress', 'in_review'))
      GROUP BY t.id, t.name, p.name, t.status
      ORDER BY MAX(te.start_time) DESC NULLS LAST, t.created_at DESC
      LIMIT ${int4}
    """.query(uuid *: nes *: nes *: nes *: int4 *: zonedDateTime.opt)

  // Get unread notifications
  val getUnreadNotifications: Query[PersonId, DashboardNotificationData] =
    sql"""
      SELECT id, user_id, title, message, notification_type, priority, is_read,
             action_url, created_at, read_at
      FROM dashboard_notifications
      WHERE user_id = $id AND is_read = FALSE
      ORDER BY created_at DESC
      LIMIT 20
    """.query(dashboardNotificationCodec)

  // Get productivity insights
  val getProductivityInsights: Query[PersonId, ProductivityInsightData] =
    sql"""
      SELECT id, user_id, category, title, description, actionable, priority, metadata,
             valid_until, is_read, created_at
      FROM productivity_insights
      WHERE user_id = $id
        AND (valid_until IS NULL OR valid_until > NOW())
        AND is_read = FALSE
      ORDER BY priority DESC, created_at DESC
      LIMIT 10
    """.query(productivityInsightCodec)

  // Get current work session
  val getCurrentWorkSession: Query[PersonId, EnhancedWorkSessionData] =
    sql"""
      SELECT id, user_id, start_time, end_time, work_mode, is_running,
             total_minutes, break_minutes, productive_minutes, description, location, created_at
      FROM enhanced_work_sessions
      WHERE user_id = $id AND is_running = TRUE
      ORDER BY start_time DESC
      LIMIT 1
    """.query(
      uuid *: id *: zonedDateTime *: zonedDateTime.opt *: nes *: bool *: int4 *: int4 *: int4 *: text.opt *: nes.opt *: zonedDateTime
    )

  // Get running time entries
  val getRunningTimeEntries: Query[PersonId, TimeEntryData] =
    sql"""
      SELECT id, user_id, task_id, work_session_id, start_time, end_time, duration,
             description, is_running, is_break, break_reason, is_manual, created_at, updated_at
      FROM time_entries
      WHERE user_id = $id AND is_running = TRUE
      ORDER BY start_time DESC
    """.query(
      uuid *: id *: uuid.opt *: uuid.opt *: zonedDateTime *: zonedDateTime.opt *: int4.opt *: text *: bool *: bool *: nes.opt *: bool *: zonedDateTime *: zonedDateTime
    )

  // Get user productivity ranking
  val getUserProductivityRanking: Query[PersonId, UserProductivityRankingData] =
    sql"""
      SELECT user_id, full_name, role, corporate_id, today_productive_minutes, today_tasks,
             today_break_minutes, week_productive_hours, week_tasks, week_efficiency,
             daily_rank, weekly_rank, current_status
      FROM user_productivity_ranking
      WHERE user_id = $id
      LIMIT 1
    """.query(
      id *: nes *: nes *: uuid *: int4 *: int4 *: int4 *: numeric *: int4 *: numeric *: int8 *: int8 *: nes
    )

  // Get team productivity overview
  val getTeamProductivityOverview: Query[PersonId, TeamProductivityOverviewData] =
    sql"""
      SELECT tpo.project_id, tpo.project_name, tpo.corporate_id, tpo.team_size,
             tpo.today_team_hours, tpo.today_avg_member_hours, tpo.active_today,
             tpo.week_team_hours, tpo.week_avg_member_hours, tpo.week_avg_efficiency,
             tpo.completed_tasks, tpo.in_progress_tasks, tpo.total_tasks
      FROM team_productivity_overview tpo
      JOIN assignees a ON tpo.project_id = (
        SELECT DISTINCT t.project_id FROM tasks t
        JOIN assignees a2 ON t.id = a2.task_id
        WHERE a2.user_id = $id
        LIMIT 1
      )
      WHERE tpo.corporate_id = (SELECT corporate_id FROM users WHERE id = $id LIMIT 1)
    """.query(
      uuid *: nes *: uuid *: int4 *: numeric *: numeric *: int4 *: numeric *: numeric *: numeric *: int4 *: int4 *: int4
    )

  // Get task completion performance
  val getTaskCompletionPerformance: Query[PersonId, TaskCompletionPerformanceData] =
    tcp"""
      SELECT task_id, task_name, project_id, status, created_at, finished_at, deadline,
             estimated_hours, actual_hours_spent, users_worked, time_entries_count,
             time_accuracy_percentage, deadline_variance_days, last_worked_on
      FROM task_completion_performance tcp
      JOIN assignees a ON tcp.task_id = a.task_id
      WHERE a.user_id = $id
        AND tcp.last_worked_on >= CURRENT_DATE - INTERVAL '30 days'
      ORDER BY tcp.last_worked_on DESC
      LIMIT 20
    """.query(
      uuid *: nes *: uuid *: nes *: zonedDateTime *: zonedDateTime.opt *: zonedDateTime.opt *: int4.opt *: numeric *: int4 *: int4 *: numeric.opt *: numeric.opt *: zonedDateTime.opt
    )

  // Calculate productivity score
  val getProductivityScore: Query[PersonId, Double] =
    sql"""
      WITH user_metrics AS (
        SELECT
          -- Focus time score (max 40 points for 8+ hours)
          LEAST(40, (dps.productive_minutes / 60.0 / 8.0) * 40) as focus_score,
          -- Efficiency score (max 30 points for 80%+ efficiency)
          CASE
            WHEN (dps.productive_minutes + dps.break_minutes) > 0
            THEN LEAST(30, (dps.productive_minutes::DECIMAL / (dps.productive_minutes + dps.break_minutes)) * 30)
            ELSE 0
          END as efficiency_score,
          -- Session consistency score (max 20 points for good session length)
          CASE
            WHEN dps.avg_session_length >= 60 THEN 20 -- 1+ hour sessions
            WHEN dps.avg_session_length >= 30 THEN 15 -- 30+ minute sessions
            ELSE 10
          END as consistency_score,
          -- Task completion score (max 10 points)
          LEAST(10, dps.tasks_worked * 2) as task_score
        FROM daily_productivity_stats dps
        WHERE dps.user_id = $id AND dps.report_date = CURRENT_DATE
      )
      SELECT COALESCE(focus_score + efficiency_score + consistency_score + task_score, 0)
      FROM user_metrics
    """.query(numeric)

  // Get hourly productivity patterns
  val getHourlyProductivityPatterns: Query[PersonId, HourlyProductivityData] =
    sql"""
      SELECT user_id, hour_of_day, day_of_week, session_count,
             avg_duration_minutes, total_duration_minutes, avg_productive_duration
      FROM hourly_productivity_patterns
      WHERE user_id = $id
      ORDER BY hour_of_day, day_of_week
    """.query(id *: int4 *: int4 *: int4 *: numeric *: numeric *: numeric)

  // Insert productivity insight
  val insertProductivityInsight: Command[ProductivityInsightData] =
    sql"""
      INSERT INTO productivity_insights (id, user_id, category, title, description, actionable,
                                       priority, metadata, valid_until, is_read, created_at)
      VALUES ($productivityInsightCodec)
    """.command

  // Insert dashboard notification
  val insertDashboardNotification: Command[DashboardNotificationData] =
    sql"""
      INSERT INTO dashboard_notifications (id, user_id, title, message, notification_type,
                                         priority, is_read, action_url, created_at, read_at)
      VALUES ($dashboardNotificationCodec)
    """.command

  // Mark notification as read
  val markNotificationAsRead: Command[(java.util.UUID, PersonId)] =
    sql"""
      UPDATE dashboard_notifications
      SET is_read = TRUE, read_at = NOW()
      WHERE id = ${uuid} AND user_id = ${id}
    """.command

  // Mark insight as read
  val markInsightAsRead: Command[(java.util.UUID, PersonId)] =
    sql"""
      UPDATE productivity_insights
      SET is_read = TRUE
      WHERE id = ${uuid} AND user_id = ${id}
    """.command

  // Refresh materialized views
  val refreshMaterializedViews: Command[Void] =
    sql"""SELECT refresh_analytics_views()""".command
}

// Data classes for database results
case class DailyProductivityData(
    userId: PersonId,
    reportDate: LocalDate,
    productiveMinutes: Int,
    breakMinutes: Int,
    productiveSessions: Int,
    breakSessions: Int,
    tasksWorked: Int,
    firstActivity: Option[java.time.ZonedDateTime],
    lastActivity: Option[java.time.ZonedDateTime],
    avgSessionLength: Option[Double],
    workMode: Option[String],
  )

case class WeeklyProductivityData(
    userId: PersonId,
    weekStart: LocalDate,
    totalProductiveHours: Double,
    totalBreakHours: Double,
    totalHours: Double,
    workDays: Int,
    avgDailyProductiveHours: Double,
    avgDailyTotalHours: Double,
    totalTasksWorked: Int,
    avgDailyTasks: Double,
    efficiencyPercentage: Double,
    totalSessions: Int,
  )

case class UserGoalsData(
    id: java.util.UUID,
    userId: PersonId,
    dailyHoursGoal: Double,
    weeklyHoursGoal: Double,
    monthlyTasksGoal: Int,
    productivityGoal: Double,
    streakGoal: Int,
    createdAt: java.time.ZonedDateTime,
    updatedAt: java.time.ZonedDateTime,
  )

case class ProductivityInsightData(
    id: java.util.UUID,
    userId: PersonId,
    category: String,
    title: String,
    description: String,
    actionable: Boolean,
    priority: String,
    metadata: io.circe.Json,
    validUntil: Option[java.time.ZonedDateTime],
    isRead: Boolean,
    createdAt: java.time.ZonedDateTime,
  )

case class DashboardNotificationData(
    id: java.util.UUID,
    userId: PersonId,
    title: String,
    message: String,
    notificationType: String,
    priority: String,
    isRead: Boolean,
    actionUrl: Option[String],
    createdAt: java.time.ZonedDateTime,
    readAt: Option[java.time.ZonedDateTime],
  )

case class RecentTaskData(
    taskId: java.util.UUID,
    taskName: String,
    projectName: String,
    status: String,
    timeSpentMinutes: Int,
    lastWorked: Option[java.time.ZonedDateTime],
  )

case class EnhancedWorkSessionData(
    id: java.util.UUID,
    userId: PersonId,
    startTime: java.time.ZonedDateTime,
    endTime: Option[java.time.ZonedDateTime],
    workMode: String,
    isRunning: Boolean,
    totalMinutes: Int,
    breakMinutes: Int,
    productiveMinutes: Int,
    description: Option[String],
    location: Option[String],
    createdAt: java.time.ZonedDateTime,
  )

case class TimeEntryData(
    id: java.util.UUID,
    userId: PersonId,
    taskId: Option[java.util.UUID],
    workSessionId: Option[java.util.UUID],
    startTime: java.time.ZonedDateTime,
    endTime: Option[java.time.ZonedDateTime],
    duration: Option[Int],
    description: String,
    isRunning: Boolean,
    isBreak: Boolean,
    breakReason: Option[String],
    isManual: Boolean,
    createdAt: java.time.ZonedDateTime,
    updatedAt: java.time.ZonedDateTime,
  )

case class UserProductivityRankingData(
    userId: PersonId,
    fullName: String,
    role: String,
    corporateId: java.util.UUID,
    todayProductiveMinutes: Int,
    todayTasks: Int,
    todayBreakMinutes: Int,
    weekProductiveHours: Double,
    weekTasks: Int,
    weekEfficiency: Double,
    dailyRank: Long,
    weeklyRank: Long,
    currentStatus: String,
  )

case class TeamProductivityOverviewData(
    projectId: java.util.UUID,
    projectName: String,
    corporateId: java.util.UUID,
    teamSize: Int,
    todayTeamHours: Double,
    todayAvgMemberHours: Double,
    activeToday: Int,
    weekTeamHours: Double,
    weekAvgMemberHours: Double,
    weekAvgEfficiency: Double,
    completedTasks: Int,
    inProgressTasks: Int,
    totalTasks: Int,
  )

case class TaskCompletionPerformanceData(
    taskId: java.util.UUID,
    taskName: String,
    projectId: java.util.UUID,
    status: String,
    createdAt: java.time.ZonedDateTime,
    finishedAt: Option[java.time.ZonedDateTime],
    deadline: Option[java.time.ZonedDateTime],
    estimatedHours: Option[Int],
    actualHoursSpent: Double,
    usersWorked: Int,
    timeEntriesCount: Int,
    timeAccuracyPercentage: Option[Double],
    deadlineVarianceDays: Option[Double],
    lastWorkedOn: Option[java.time.ZonedDateTime],
  )

case class HourlyProductivityData(
    userId: PersonId,
    hourOfDay: Int,
    dayOfWeek: Int,
    sessionCount: Int,
    avgDurationMinutes: Double,
    totalDurationMinutes: Double,
    avgProductiveDuration: Double,
  )
