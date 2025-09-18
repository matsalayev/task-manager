package tm.domain.time

import java.time.LocalDate
import java.time.LocalDateTime

import io.circe.Codec
import io.circe.generic.semiauto._
import monocle.Iso

import tm.domain.PersonId
import tm.domain.TaskId
import tm.domain.WorkId
import tm.effects.IsUUID
import tm.syntax.circe._

case class EnhancedWorkSession(
    id: WorkId,
    userId: PersonId,
    startTime: LocalDateTime,
    endTime: Option[LocalDateTime],
    workMode: WorkMode,
    isRunning: Boolean,
    totalMinutes: Int,
    breakMinutes: Int,
    productiveMinutes: Int,
    description: Option[String],
    location: Option[String],
    createdAt: java.time.ZonedDateTime,
  )

sealed trait WorkMode
object WorkMode {
  case object Office extends WorkMode
  case object Remote extends WorkMode
  case object Hybrid extends WorkMode

  implicit val codec: Codec[WorkMode] = deriveCodec
}

case class TimeEntry(
    id: TimeEntryId,
    userId: PersonId,
    taskId: Option[TaskId],
    workSessionId: Option[WorkId],
    startTime: LocalDateTime,
    endTime: Option[LocalDateTime],
    duration: Option[Int], // minutes
    description: String,
    isRunning: Boolean,
    isBreak: Boolean,
    breakReason: Option[BreakReason],
    isManual: Boolean,
    createdAt: java.time.ZonedDateTime,
    updatedAt: java.time.ZonedDateTime,
  )

case class TimeEntryId(value: java.util.UUID) extends AnyVal

sealed trait BreakReason
object BreakReason {
  case object Lunch extends BreakReason
  case object Coffee extends BreakReason
  case object Meeting extends BreakReason
  case object Personal extends BreakReason
  case object Toilet extends BreakReason
  case object Other extends BreakReason

  implicit val codec: Codec[BreakReason] = deriveCodec
}

case class DailyTimeReport(
    userId: PersonId,
    date: LocalDate,
    totalWorkedMinutes: Int,
    productiveMinutes: Int,
    breakMinutes: Int,
    tasksWorked: Int,
    workMode: Option[WorkMode],
    startTime: Option[LocalDateTime],
    endTime: Option[LocalDateTime],
    overtimeMinutes: Int,
    isHoliday: Boolean,
  )

case class WeeklyTimeReport(
    userId: PersonId,
    weekStart: LocalDate,
    totalWorkedHours: Double,
    productiveHours: Double,
    overtimeHours: Double,
    workDays: Int,
    averageDailyHours: Double,
    dailyReports: List[DailyTimeReport],
  )

case class ProductivityMetrics(
    focusTimeHours: Double,
    averageSessionLength: Double,
    breakFrequency: Double,
    peakProductivityHours: List[Int],
    taskSwitchCount: Int,
    timePerTask: Double,
  )

case class TimeDashboard(
    currentSession: Option[EnhancedWorkSession],
    currentTimer: Option[TimeEntry],
    todayStats: DailyTimeReport,
    weekProgress: WeekProgress,
    recentActivities: List[ActivityLog],
    productivityScore: Double,
  )

case class WeekProgress(
    weekStart: LocalDate,
    targetHours: Double,
    workedHours: Double,
    remainingHours: Double,
    progressPercentage: Double,
    dailyBreakdown: List[DailyProgress],
  )

case class DailyProgress(
    date: LocalDate,
    workedHours: Double,
    targetHours: Double,
    isToday: Boolean,
  )

case class ActivityLog(
    id: ActivityLogId,
    userId: PersonId,
    activityType: ActivityType,
    timestamp: LocalDateTime,
    metadata: Map[String, String],
    createdAt: java.time.ZonedDateTime,
  )

case class ActivityLogId(value: java.util.UUID) extends AnyVal

sealed trait ActivityType
object ActivityType {
  case object SessionStart extends ActivityType
  case object SessionEnd extends ActivityType
  case object BreakStart extends ActivityType
  case object BreakEnd extends ActivityType
  case object TaskStart extends ActivityType
  case object TaskEnd extends ActivityType
  case object ModeChange extends ActivityType

  implicit val codec: Codec[ActivityType] = deriveCodec
}

// Request/Response DTOs
case class StartWorkSessionRequest(
    workMode: WorkMode,
    location: Option[String],
    description: Option[String],
  )

case class StartTimerRequest(
    taskId: Option[TaskId],
    description: String,
  )

case class StartBreakRequest(
    reason: BreakReason,
    description: Option[String],
  )

case class ManualTimeEntryRequest(
    taskId: Option[TaskId],
    startTime: LocalDateTime,
    durationMinutes: Int,
    description: String,
  )

case class TimeEntriesFilterRequest(
    taskId: Option[TaskId] = None,
    startDate: Option[LocalDate] = None,
    endDate: Option[LocalDate] = None,
  )

// Codecs
object TimeEntryId {
  implicit val codec: Codec[TimeEntryId] = deriveCodec
  implicit val isUUID: IsUUID[TimeEntryId] = new IsUUID[TimeEntryId] {
    val uuid: Iso[java.util.UUID, TimeEntryId] = Iso(TimeEntryId(_))(_.value)
  }
}

object EnhancedWorkSession {
  implicit val codec: Codec[EnhancedWorkSession] = deriveCodec
}

object TimeEntry {
  implicit val codec: Codec[TimeEntry] = deriveCodec
}

object DailyTimeReport {
  implicit val codec: Codec[DailyTimeReport] = deriveCodec
}

object WeeklyTimeReport {
  implicit val codec: Codec[WeeklyTimeReport] = deriveCodec
}

object ProductivityMetrics {
  implicit val codec: Codec[ProductivityMetrics] = deriveCodec
}

object TimeDashboard {
  implicit val codec: Codec[TimeDashboard] = deriveCodec
}

object WeekProgress {
  implicit val codec: Codec[WeekProgress] = deriveCodec
}

object DailyProgress {
  implicit val codec: Codec[DailyProgress] = deriveCodec
}

object ActivityLog {
  implicit val codec: Codec[ActivityLog] = deriveCodec
}

object ActivityLogId {
  implicit val codec: Codec[ActivityLogId] = deriveCodec
  implicit val isUUID: IsUUID[ActivityLogId] = new IsUUID[ActivityLogId] {
    val uuid: Iso[java.util.UUID, ActivityLogId] = Iso(ActivityLogId(_))(_.value)
  }
}

object StartWorkSessionRequest {
  implicit val codec: Codec[StartWorkSessionRequest] = deriveCodec
}

object StartTimerRequest {
  implicit val codec: Codec[StartTimerRequest] = deriveCodec
}

object StartBreakRequest {
  implicit val codec: Codec[StartBreakRequest] = deriveCodec
}

object ManualTimeEntryRequest {
  implicit val codec: Codec[ManualTimeEntryRequest] = deriveCodec
}

object TimeEntriesFilterRequest {
  implicit val codec: Codec[TimeEntriesFilterRequest] = deriveCodec
}
