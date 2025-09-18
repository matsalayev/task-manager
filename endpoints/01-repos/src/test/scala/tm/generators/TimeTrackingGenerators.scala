package tm.generators

import java.time.ZonedDateTime
import java.util.UUID

import tm.domain.PersonId
import tm.domain.TaskId
import tm.domain.WorkId
import tm.domain.time._

object TimeTrackingGenerators {
  def workModeGen: WorkMode = WorkMode.Office

  def breakReasonGen: BreakReason = BreakReason.Coffee

  def activityTypeGen: ActivityType = ActivityType.TaskStart

  def enhancedWorkSessionGen: EnhancedWorkSession = EnhancedWorkSession(
    id = WorkId(UUID.randomUUID()),
    userId = PersonId(UUID.randomUUID()),
    startTime = java.time.LocalDateTime.now(),
    endTime = Some(java.time.LocalDateTime.now().plusHours(8)),
    workMode = WorkMode.Office,
    isRunning = false,
    totalMinutes = 480,
    breakMinutes = 60,
    productiveMinutes = 420,
    description = Some("Daily work session"),
    location = Some("Office"),
    createdAt = ZonedDateTime.now(),
  )

  def timeEntryGen: TimeEntry = TimeEntry(
    id = TimeEntryId(UUID.randomUUID()),
    userId = PersonId(UUID.randomUUID()),
    taskId = Some(TaskId(UUID.randomUUID())),
    workSessionId = Some(WorkId(UUID.randomUUID())),
    startTime = java.time.LocalDateTime.now(),
    endTime = Some(java.time.LocalDateTime.now().plusHours(2)),
    duration = Some(120),
    description = "Working on task",
    isRunning = false,
    isBreak = false,
    breakReason = None,
    isManual = false,
    createdAt = ZonedDateTime.now(),
    updatedAt = ZonedDateTime.now(),
  )

  def activityLogGen: ActivityLog = ActivityLog(
    id = ActivityLogId(UUID.randomUUID()),
    userId = PersonId(UUID.randomUUID()),
    activityType = ActivityType.TaskStart,
    timestamp = java.time.LocalDateTime.now(),
    metadata = Map.empty[String, String],
    createdAt = ZonedDateTime.now(),
  )

  def startWorkSessionRequestGen: StartWorkSessionRequest = StartWorkSessionRequest(
    workMode = WorkMode.Remote,
    location = Some("Home"),
    description = Some("Remote work session"),
  )

  def startTimerRequestGen: StartTimerRequest = StartTimerRequest(
    taskId = Some(TaskId(UUID.randomUUID())),
    description = "Time entry for task",
  )

  def zonedDateTimeGen: ZonedDateTime = ZonedDateTime.now()
}
