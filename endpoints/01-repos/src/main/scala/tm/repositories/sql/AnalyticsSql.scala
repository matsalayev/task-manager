package tm.repositories.sql

import java.time.LocalDate

import skunk._
import skunk.codec.all._
import skunk.implicits._

import tm.domain.PersonId
import tm.repositories.sql.AnalyticsData._
import tm.support.skunk.Sql
import tm.support.skunk.codecs._

private[repositories] object AnalyticsSql extends Sql[PersonId] {

  // Daily Productivity Stats Codec
  val dailyProductivityStatsCodec: Codec[DailyProductivityData] =
    (id *: date *: int4 *: int4 *: int4 *: int4 *: int4 *: zonedDateTime.opt *: zonedDateTime.opt *: numeric.opt *: nes.opt)
      .imap { tuple =>
        val userId *: reportDate *: productiveMinutes *: breakMinutes *: tasksWorked *: totalMinutes *: sessionCount *: firstActivity *: lastActivity *: efficiency *: workMode *: _ =
          tuple
        DailyProductivityData(
          userId,
          reportDate,
          productiveMinutes,
          breakMinutes,
          tasksWorked,
          totalMinutes,
          sessionCount,
          firstActivity,
          lastActivity,
          efficiency,
          workMode,
        )
      } {
        case DailyProductivityData(
               userId,
               reportDate,
               productiveMinutes,
               breakMinutes,
               tasksWorked,
               totalMinutes,
               sessionCount,
               firstActivity,
               lastActivity,
               efficiency,
               workMode,
             ) =>
          userId *: reportDate *: productiveMinutes *: breakMinutes *: tasksWorked *: totalMinutes *: sessionCount *: firstActivity *: lastActivity *: efficiency *: workMode *: EmptyTuple
      }

  // Get today's stats for a user
  val getTodayStats: Query[PersonId, DailyProductivityData] =
    sql"""
      SELECT user_id, current_date as report_date,
             COALESCE(SUM(CASE WHEN NOT is_break THEN duration_minutes ELSE 0 END), 0) as productive_minutes,
             COALESCE(SUM(CASE WHEN is_break THEN duration_minutes ELSE 0 END), 0) as break_minutes,
             COUNT(DISTINCT task_id) FILTER (WHERE task_id IS NOT NULL) as tasks_worked,
             COALESCE(SUM(duration_minutes), 0) as total_minutes,
             COUNT(*) as session_count,
             MIN(start_time) as first_activity,
             MAX(COALESCE(end_time, start_time)) as last_activity,
             CASE WHEN SUM(duration_minutes) > 0
                  THEN ROUND(SUM(CASE WHEN NOT is_break THEN duration_minutes ELSE 0 END) * 100.0 / SUM(duration_minutes), 2)
                  ELSE 0 END as efficiency,
             NULL::text as work_mode
      FROM time_entries
      WHERE user_id = $id AND DATE(start_time) = CURRENT_DATE
      GROUP BY user_id
      LIMIT 1
    """.query(dailyProductivityStatsCodec)

  // Get user goals
  val getUserGoals: Query[PersonId, UserGoalsData] =
    sql"""
      SELECT id, user_id, daily_hours_goal, weekly_hours_goal, monthly_tasks_goal,
             productivity_goal, streak_goal, created_at, updated_at
      FROM user_goals
      WHERE user_id = $id AND deleted_at IS NULL
      LIMIT 1
    """
      .query(
        uuid *: id *: numeric *: numeric *: int4 *: numeric *: int4 *: zonedDateTime *: zonedDateTime
      )
      .map { tuple =>
        val goalId *: userId *: dailyHours *: weeklyHours *: monthlyTasks *: productivity *: streak *: createdAt *: updatedAt *: _ =
          tuple
        UserGoalsData(
          goalId,
          userId,
          dailyHours,
          weeklyHours,
          monthlyTasks,
          productivity,
          streak,
          createdAt,
          updatedAt,
        )
      }

  // Upsert user goals
  val upsertUserGoals: Command[UserGoalsData] =
    sql"""
      INSERT INTO user_goals (id, user_id, daily_hours_goal, weekly_hours_goal, monthly_tasks_goal,
                              productivity_goal, streak_goal, created_at, updated_at)
      VALUES (${uuid}, ${id}, ${numeric}, ${numeric}, ${int4}, ${numeric}, ${int4}, ${zonedDateTime}, ${zonedDateTime})
      ON CONFLICT (user_id) WHERE deleted_at IS NULL
      DO UPDATE SET
        daily_hours_goal = EXCLUDED.daily_hours_goal,
        weekly_hours_goal = EXCLUDED.weekly_hours_goal,
        monthly_tasks_goal = EXCLUDED.monthly_tasks_goal,
        productivity_goal = EXCLUDED.productivity_goal,
        streak_goal = EXCLUDED.streak_goal,
        updated_at = EXCLUDED.updated_at
    """.command.contramap[UserGoalsData] { goals =>
      goals.id *: goals.userId *: goals.dailyHoursGoal *: goals.weeklyHoursGoal *: goals.monthlyTasksGoal *:
        goals.productivityGoal *: goals.streakGoal *: goals.createdAt *: goals.updatedAt *: EmptyTuple
    }

  // Get recent tasks
  val getRecentTasks: Query[(PersonId, Int), RecentTaskData] =
    sql"""
      SELECT DISTINCT t.id, t.name, p.name as project_name, t.status::text,
             COALESCE(SUM(te.duration_minutes), 0) as time_spent,
             MAX(te.end_time) as last_worked
      FROM tasks t
      JOIN projects p ON t.project_id = p.id
      LEFT JOIN time_entries te ON te.task_id = t.id
      JOIN task_assignees ta ON ta.task_id = t.id
      WHERE ta.assignee_id = $id AND t.deleted_at IS NULL
      GROUP BY t.id, t.name, p.name, t.status
      ORDER BY last_worked DESC NULLS LAST
      LIMIT $int4
    """
      .query(uuid *: nes *: nes *: nes *: int4 *: zonedDateTime.opt)
      .contramap[(PersonId, Int)] { case (userId, limit) => userId *: limit *: EmptyTuple }
      .map { tuple =>
        val taskId *: name *: projectName *: status *: timeSpent *: lastWorked *: _ = tuple
        RecentTaskData(taskId, name, projectName, status, timeSpent, lastWorked)
      }

  // Get current work session
  val getCurrentWorkSession: Query[PersonId, EnhancedWorkSessionData] =
    sql"""
      SELECT id, user_id, start_time, end_time, work_mode::text, is_running,
             total_minutes, break_minutes, productive_minutes, description, location::text, created_at
      FROM enhanced_work_sessions
      WHERE user_id = $id AND is_running = true AND deleted_at IS NULL
      ORDER BY start_time DESC
      LIMIT 1
    """
      .query(
        uuid *: id *: zonedDateTime *: zonedDateTime.opt *: nes *: bool *: int4 *: int4 *: int4 *: text.opt *: nes.opt *: zonedDateTime
      )
      .map { tuple =>
        val sessionId *: userId *: startTime *: endTime *: workMode *: isRunning *: totalMinutes *: breakMinutes *: productiveMinutes *: description *: location *: createdAt *: _ =
          tuple
        EnhancedWorkSessionData(
          sessionId,
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
      }

  // Get running time entries
  val getRunningTimeEntries: Query[PersonId, TimeEntryData] =
    sql"""
      SELECT id, user_id, task_id, work_session_id, start_time, end_time, duration_minutes,
             description, is_running, is_break, break_reason::text, is_manual, created_at, updated_at
      FROM time_entries
      WHERE user_id = $id AND is_running = true
      ORDER BY start_time DESC
    """
      .query(
        uuid *: id *: uuid.opt *: uuid.opt *: zonedDateTime *: zonedDateTime.opt *: int4.opt *: text *: bool *: bool *: nes.opt *: bool *: zonedDateTime *: zonedDateTime
      )
      .map { tuple =>
        val entryId *: userId *: taskId *: workSessionId *: startTime *: endTime *: duration *: description *: isRunning *: isBreak *: breakReason *: isManual *: createdAt *: updatedAt *: _ =
          tuple
        TimeEntryData(
          entryId,
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

  // Get productivity score
  val getProductivityScore: Query[PersonId, Double] =
    sql"""
      SELECT CASE
               WHEN SUM(total_minutes) > 0
               THEN ROUND(SUM(productive_minutes) * 100.0 / SUM(total_minutes), 2)
               ELSE 0
             END as productivity_score
      FROM time_entries
      WHERE user_id = $id AND DATE(start_time) >= CURRENT_DATE - INTERVAL '30 days'
    """.query(numeric.map(_.toDouble))

  // Simple placeholder queries for other methods to compile
  val getWeekStats: Query[(PersonId, LocalDate), WeeklyProductivityData] =
    sql"SELECT $id, $date, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0"
      .query(
        id *: date *: numeric *: numeric *: numeric *: int4 *: numeric *: numeric *: int4 *: numeric *: numeric *: int4
      )
      .contramap[(PersonId, LocalDate)] {
        case (userId, weekStart) => userId *: weekStart *: EmptyTuple
      }
      .map { tuple =>
        val userId *: weekStart *: totalProd *: totalBreak *: overtime *: workDays *: avgDaily *: avgSession *: totalTasks *: totalHours *: prodHours *: breakHours *: _ =
          tuple
        WeeklyProductivityData(
          userId,
          weekStart,
          totalProd,
          totalBreak,
          overtime,
          workDays,
          avgDaily,
          avgSession,
          totalTasks,
          totalHours,
          prodHours,
          breakHours,
        )
      }

  val getUnreadNotifications: Query[PersonId, DashboardNotificationData] =
    sql"SELECT gen_random_uuid(), $id, 'Title', 'Message', 'info', 'medium', false, NULL, NOW(), NULL"
      .query(
        uuid *: id *: nes *: text *: nes *: nes *: bool *: nes.opt *: zonedDateTime *: zonedDateTime.opt
      )
      .map { tuple =>
        val notifId *: userId *: title *: message *: notifType *: priority *: isRead *: actionUrl *: createdAt *: validUntil *: _ =
          tuple
        DashboardNotificationData(
          notifId,
          userId,
          title,
          message,
          notifType,
          priority,
          isRead,
          actionUrl,
          createdAt,
          validUntil,
        )
      }

  val getProductivityInsights: Query[PersonId, ProductivityInsightData] =
    sql"SELECT gen_random_uuid(), $id, 'productivity', 'Insight', 'Description', true, 'medium', '{}', NULL, false, NOW()"
      .query(
        uuid *: id *: nes *: nes *: text *: bool *: nes *: text *: zonedDateTime.opt *: bool *: zonedDateTime
      )
      .map { tuple =>
        val insightId *: userId *: category *: title *: description *: actionable *: priority *: metadata *: validUntil *: isRead *: createdAt *: _ =
          tuple
        ProductivityInsightData(
          insightId,
          userId,
          category,
          title,
          description,
          actionable,
          priority,
          metadata,
          validUntil,
          isRead,
          createdAt,
        )
      }

  // Placeholder commands
  val insertProductivityInsight: Command[ProductivityInsightData] =
    sql"INSERT INTO productivity_insights (id, user_id) VALUES (${uuid}, ${id})"
      .command
      .contramap[ProductivityInsightData](insight => insight.id *: insight.userId *: EmptyTuple)

  val insertDashboardNotification: Command[DashboardNotificationData] =
    sql"INSERT INTO dashboard_notifications (id, user_id) VALUES (${uuid}, ${id})"
      .command
      .contramap[DashboardNotificationData](notif => notif.id *: notif.userId *: EmptyTuple)

  val markNotificationAsRead: Command[(java.util.UUID, PersonId)] =
    sql"UPDATE dashboard_notifications SET is_read = true WHERE id = $uuid AND user_id = $id"
      .command
      .contramap[(java.util.UUID, PersonId)] {
        case (notifId, userId) => notifId *: userId *: EmptyTuple
      }

  val markInsightAsRead: Command[(java.util.UUID, PersonId)] =
    sql"UPDATE productivity_insights SET is_read = true WHERE id = $uuid AND user_id = $id"
      .command
      .contramap[(java.util.UUID, PersonId)] {
        case (insightId, userId) => insightId *: userId *: EmptyTuple
      }

  val refreshMaterializedViews: Command[skunk.Void] =
    sql"SELECT 1".command

  // Placeholder implementations for other complex queries
  val getUserProductivityRanking: Query[PersonId, UserProductivityRankingData] =
    sql"SELECT $id, 'First', 'Last', gen_random_uuid(), 0, 0, 0, 0, 1, 0, 0, 0, 'office'"
      .query(
        id *: nes *: nes *: uuid *: int4 *: int4 *: int4 *: numeric *: int4 *: numeric *: int8 *: int8 *: nes
      )
      .map { tuple =>
        val userId *: firstName *: lastName *: corporateId *: todayProd *: weekProd *: todayTasks *: efficiency *: dailyRank *: weeklyRank *: totalTime *: activeTime *: workMode *: _ =
          tuple
        UserProductivityRankingData(
          userId,
          firstName,
          lastName,
          corporateId,
          todayProd,
          weekProd,
          todayTasks,
          efficiency,
          dailyRank,
          weeklyRank,
          totalTime,
          activeTime,
          workMode,
        )
      }

  val getHourlyProductivityPatterns: Query[PersonId, HourlyProductivityData] =
    sql"SELECT $id, 9, 60, 60, 100.0, 1.0, 0.2"
      .query(
        id *: int4 *: int4 *: int4 *: numeric *: numeric *: numeric
      )
      .map { tuple =>
        val userId *: hour *: productiveMinutes *: totalMinutes *: efficiency *: taskSwitches *: breakCount *: _ =
          tuple
        HourlyProductivityData(
          userId,
          hour,
          productiveMinutes,
          totalMinutes,
          efficiency,
          taskSwitches,
          breakCount,
        )
      }

  val getTaskCompletionPerformance: Query[PersonId, TaskCompletionPerformanceData] =
    sql"SELECT $id, gen_random_uuid(), 'Task', gen_random_uuid(), 120, 150.0, 80.0, NOW(), 3.0, 85.0, 1, 1, 1"
      .query(
        id *: uuid *: nes *: uuid *: int4 *: numeric *: numeric *: zonedDateTime *: numeric *: numeric *: int4 *: int4 *: int4
      )
      .map { tuple =>
        val userId *: taskId *: taskName *: projectId *: estimated *: actual *: efficiency *: completion *: daysToComplete *: performance *: complexity *: priority *: teamSize *: _ =
          tuple
        TaskCompletionPerformanceData(
          userId,
          taskId,
          taskName,
          projectId,
          estimated,
          actual,
          efficiency,
          completion,
          daysToComplete,
          performance,
          complexity,
          priority,
          teamSize,
        )
      }

  val getTeamProductivityOverview: Query[PersonId, TeamProductivityOverviewData] =
    sql"SELECT gen_random_uuid(), 'Project', gen_random_uuid(), 5, 480.0, 2400.0, 4, 85.0, 10.0, 8.0, 2, 1, 0 WHERE $id IS NOT NULL"
      .query(
        uuid *: nes *: uuid *: int4 *: numeric *: numeric *: int4 *: numeric *: numeric *: numeric *: int4 *: int4 *: int4
      )
      .map { tuple =>
        val projectId *: projectName *: managerId *: teamSize *: todayMinutes *: weekMinutes *: activeToday *: avgProd *: totalTasks *: completedTasks *: efficiency *: inProgress *: overdue *: _ =
          tuple
        TeamProductivityOverviewData(
          projectId,
          projectName,
          managerId,
          teamSize,
          todayMinutes,
          weekMinutes,
          activeToday,
          avgProd,
          totalTasks,
          completedTasks,
          efficiency,
          inProgress,
          overdue,
        )
      }
}
