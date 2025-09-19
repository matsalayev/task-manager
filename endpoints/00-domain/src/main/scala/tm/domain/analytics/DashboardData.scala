package tm.domain.analytics

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

import enumeratum._
import io.circe.Codec
import io.circe.generic.semiauto._

import tm.domain.TaskId
import tm.domain.corporate.User
import tm.domain.time.EnhancedWorkSession
import tm.syntax.circe._

case class DashboardData(
    user: User,
    currentPeriod: DashboardPeriod,
    workSession: Option[EnhancedWorkSession],
    todayStats: TodayStats,
    weekStats: WeekStats,
    monthStats: MonthStats,
    goals: UserGoals,
    recentTasks: List[RecentTask],
    notifications: List[DashboardNotification],
    insights: List[ProductivityInsight],
  )

sealed trait DashboardPeriod extends EnumEntry
object DashboardPeriod extends Enum[DashboardPeriod] with CirceEnum[DashboardPeriod] {
  case object Today extends DashboardPeriod
  case object Week extends DashboardPeriod
  case object Month extends DashboardPeriod
  case object Year extends DashboardPeriod

  val values = findValues
}

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
    workMode: Option[tm.domain.time.WorkMode],
    startTime: Option[LocalDateTime],
    estimatedEndTime: Option[LocalDateTime],
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
    comparisonWithLastWeek: ComparisonStats,
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
    weeklyBreakdown: List[WeekBreakdown],
  )

case class DayBreakdown(
    date: LocalDate,
    workedHours: Double,
    targetHours: Double,
    efficiency: Double,
    tasksCompleted: Int,
    isToday: Boolean,
  )

case class WeekBreakdown(
    weekStart: LocalDate,
    totalHours: Double,
    efficiency: Double,
    tasksCompleted: Int,
  )

case class ComparisonStats(
    changePercentage: Double,
    direction: TrendDirection,
    description: String,
  )

sealed trait TrendDirection extends EnumEntry
object TrendDirection extends Enum[TrendDirection] with CirceEnum[TrendDirection] {
  case object Up extends TrendDirection
  case object Down extends TrendDirection
  case object Stable extends TrendDirection

  val values = findValues
}

case class ActiveWorkSession(
    id: tm.domain.WorkId,
    startTime: LocalDateTime,
    currentDuration: Int, // minutes
    workMode: tm.domain.time.WorkMode,
    currentTask: Option[TaskInfo],
    breaksSinceStart: Int,
    productiveTime: Int,
    isOnBreak: Boolean,
    breakStartTime: Option[LocalDateTime],
  )

case class TaskInfo(
    id: TaskId,
    name: String,
    projectName: String,
    description: Option[String],
  )

case class UserGoals(
    dailyHoursGoal: Double,
    weeklyHoursGoal: Double,
    monthlyTasksGoal: Int,
    productivityGoal: Double, // percentage
    streakGoal: Int, // consecutive work days
    currentProgress: GoalProgress,
  )

case class GoalProgress(
    dailyProgress: Double,
    weeklyProgress: Double,
    monthlyProgress: Double,
    streakProgress: Int,
    productivityProgress: Double,
  )

case class RecentTask(
    id: TaskId,
    name: String,
    projectName: String,
    status: tm.domain.enums.TaskStatus,
    timeSpent: Int, // minutes
    lastWorked: LocalDateTime,
  )

case class DashboardNotification(
    id: String,
    title: String,
    message: String,
    notificationType: NotificationType,
    priority: NotificationPriority,
    isRead: Boolean,
    createdAt: LocalDateTime,
    actionUrl: Option[String],
  )

sealed trait NotificationType extends EnumEntry
object NotificationType extends Enum[NotificationType] with CirceEnum[NotificationType] {
  case object TaskDeadline extends NotificationType
  case object ProjectUpdate extends NotificationType
  case object GoalAchievement extends NotificationType
  case object ProductivityAlert extends NotificationType
  case object TeamUpdate extends NotificationType

  val values = findValues
}

sealed trait NotificationPriority extends EnumEntry
object NotificationPriority
    extends Enum[NotificationPriority]
       with CirceEnum[NotificationPriority] {
  case object Low extends NotificationPriority
  case object Medium extends NotificationPriority
  case object High extends NotificationPriority
  case object Critical extends NotificationPriority

  val values = findValues
}

case class ProductivityInsight(
    id: ProductivityInsightId,
    category: InsightCategory,
    title: String,
    description: String,
    actionable: Boolean,
    priority: InsightPriority,
    metadata: Map[String, String],
    validUntil: Option[LocalDateTime],
    createdAt: LocalDateTime,
  )

sealed trait InsightCategory extends EnumEntry
object InsightCategory extends Enum[InsightCategory] with CirceEnum[InsightCategory] {
  case object Productivity extends InsightCategory
  case object TimeManagement extends InsightCategory
  case object WorkLifeBalance extends InsightCategory
  case object Goals extends InsightCategory
  case object Team extends InsightCategory

  val values = findValues
}

sealed trait InsightPriority extends EnumEntry
object InsightPriority extends Enum[InsightPriority] with CirceEnum[InsightPriority] {
  case object Low extends InsightPriority
  case object Medium extends InsightPriority
  case object High extends InsightPriority

  val values = findValues
}

// Codecs
object DashboardData {
  implicit val codec: Codec[DashboardData] = deriveCodec
}

object TodayStats {
  implicit val codec: Codec[TodayStats] = deriveCodec
}

object WeekStats {
  implicit val codec: Codec[WeekStats] = deriveCodec
}

object MonthStats {
  implicit val codec: Codec[MonthStats] = deriveCodec
}

object DayBreakdown {
  implicit val codec: Codec[DayBreakdown] = deriveCodec
}

object WeekBreakdown {
  implicit val codec: Codec[WeekBreakdown] = deriveCodec
}

object ComparisonStats {
  implicit val codec: Codec[ComparisonStats] = deriveCodec
}

object ActiveWorkSession {
  implicit val codec: Codec[ActiveWorkSession] = deriveCodec
}

object TaskInfo {
  implicit val codec: Codec[TaskInfo] = deriveCodec
}

object UserGoals {
  implicit val codec: Codec[UserGoals] = deriveCodec
}

object GoalProgress {
  implicit val codec: Codec[GoalProgress] = deriveCodec
}

object RecentTask {
  implicit val codec: Codec[RecentTask] = deriveCodec
}

object DashboardNotification {
  implicit val codec: Codec[DashboardNotification] = deriveCodec
}

object ProductivityInsight {
  implicit val codec: Codec[ProductivityInsight] = deriveCodec
}
