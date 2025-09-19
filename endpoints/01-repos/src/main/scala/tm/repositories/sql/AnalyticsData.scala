package tm.repositories.sql

import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.PersonId

object AnalyticsData {
  // SQL layer data types - DTOs for repository queries
  case class DailyProductivityData(
      userId: PersonId,
      reportDate: LocalDate,
      productiveMinutes: Int,
      breakMinutes: Int,
      tasksWorked: Int,
      totalMinutes: Int,
      sessionCount: Int,
      firstActivity: Option[ZonedDateTime],
      lastActivity: Option[ZonedDateTime],
      efficiency: Option[BigDecimal],
      workMode: Option[NonEmptyString],
    )

  case class WeeklyProductivityData(
      userId: PersonId,
      weekStart: LocalDate,
      totalProductiveMinutes: BigDecimal,
      totalBreakMinutes: BigDecimal,
      overtimeMinutes: BigDecimal,
      workDays: Int,
      avgDailyProductive: BigDecimal,
      avgSessionLength: BigDecimal,
      totalTasksWorked: Int,
      totalHours: BigDecimal,
      productiveHours: BigDecimal,
      breakHours: Int,
    )

  case class UserGoalsData(
      id: UUID,
      userId: PersonId,
      dailyHoursGoal: BigDecimal,
      weeklyHoursGoal: BigDecimal,
      monthlyTasksGoal: Int,
      productivityGoal: BigDecimal,
      streakGoal: Int,
      createdAt: ZonedDateTime,
      updatedAt: ZonedDateTime,
    )

  case class RecentTaskData(
      id: UUID,
      name: NonEmptyString,
      projectName: NonEmptyString,
      status: NonEmptyString,
      timeSpent: Int,
      lastWorked: Option[ZonedDateTime],
    )

  case class DashboardNotificationData(
      id: UUID,
      userId: PersonId,
      title: NonEmptyString,
      message: String,
      notificationType: NonEmptyString,
      priority: NonEmptyString,
      isRead: Boolean,
      actionUrl: Option[NonEmptyString],
      createdAt: ZonedDateTime,
      validUntil: Option[ZonedDateTime],
    )

  case class ProductivityInsightData(
      id: UUID,
      userId: PersonId,
      category: NonEmptyString,
      title: NonEmptyString,
      description: String,
      actionable: Boolean,
      priority: NonEmptyString,
      metadata: String, // JSON as string
      validUntil: Option[ZonedDateTime],
      isRead: Boolean,
      createdAt: ZonedDateTime,
    )

  case class EnhancedWorkSessionData(
      id: UUID,
      userId: PersonId,
      startTime: ZonedDateTime,
      endTime: Option[ZonedDateTime],
      workMode: NonEmptyString,
      isRunning: Boolean,
      totalMinutes: Int,
      breakMinutes: Int,
      productiveMinutes: Int,
      description: Option[String],
      location: Option[NonEmptyString],
      createdAt: ZonedDateTime,
    )

  case class TimeEntryData(
      id: UUID,
      userId: PersonId,
      taskId: Option[UUID],
      workSessionId: Option[UUID],
      startTime: ZonedDateTime,
      endTime: Option[ZonedDateTime],
      duration: Option[Int],
      description: String,
      isRunning: Boolean,
      isBreak: Boolean,
      breakReason: Option[NonEmptyString],
      isManual: Boolean,
      createdAt: ZonedDateTime,
      updatedAt: ZonedDateTime,
    )

  case class UserProductivityRankingData(
      userId: PersonId,
      firstName: NonEmptyString,
      lastName: NonEmptyString,
      corporateId: UUID,
      todayProductiveMinutes: Int,
      weekProductiveMinutes: Int,
      todayTasks: Int,
      efficiency: BigDecimal,
      dailyRank: Int,
      weeklyRank: BigDecimal,
      totalTime: Long,
      activeTime: Long,
      workMode: NonEmptyString,
    )

  case class HourlyProductivityData(
      userId: PersonId,
      hour: Int,
      productiveMinutes: Int,
      totalMinutes: Int,
      efficiency: BigDecimal,
      taskSwitches: BigDecimal,
      breakCount: BigDecimal,
    )

  case class TaskCompletionPerformanceData(
      userId: PersonId,
      taskId: UUID,
      taskName: NonEmptyString,
      projectId: UUID,
      estimatedMinutes: Int,
      actualMinutes: BigDecimal,
      efficiency: BigDecimal,
      completionDate: ZonedDateTime,
      daysToComplete: BigDecimal,
      performance: BigDecimal,
      complexity: Int,
      priority: Int,
      teamSize: Int,
    )

  case class TeamProductivityOverviewData(
      projectId: UUID,
      projectName: NonEmptyString,
      managerId: UUID,
      teamSize: Int,
      todayTotalMinutes: BigDecimal,
      weekTotalMinutes: BigDecimal,
      activeToday: Int,
      avgProductivity: BigDecimal,
      totalTasks: BigDecimal,
      completedTasks: BigDecimal,
      efficiency: Int,
      tasksInProgress: Int,
      overdueTasks: Int,
    )
}
