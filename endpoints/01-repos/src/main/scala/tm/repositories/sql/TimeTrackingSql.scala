package tm.repositories.sql

import skunk._
import skunk.codec.all._
import skunk.implicits._

import tm.domain.PersonId
import tm.domain.TaskId
import tm.domain.WorkId
import tm.domain.time._
import tm.support.skunk.Sql
import tm.support.skunk.codecs._

private[repositories] object TimeTrackingSql extends Sql[WorkId] {

  // WorkMode codec
  private val workMode: Codec[WorkMode] = varchar.imap[WorkMode] {
    case "Office" => WorkMode.Office
    case "Remote" => WorkMode.Remote
    case "Hybrid" => WorkMode.Hybrid
    case other => throw new RuntimeException(s"Unknown work mode: $other")
  } {
    case WorkMode.Office => "Office"
    case WorkMode.Remote => "Remote"
    case WorkMode.Hybrid => "Hybrid"
  }

  // BreakReason codec
  private val breakReason: Codec[BreakReason] = varchar.imap[BreakReason] {
    case "Lunch" => BreakReason.Lunch
    case "Coffee" => BreakReason.Coffee
    case "Meeting" => BreakReason.Meeting
    case "Personal" => BreakReason.Personal
    case "Toilet" => BreakReason.Toilet
    case "Other" => BreakReason.Other
    case other => throw new RuntimeException(s"Unknown break reason: $other")
  } {
    case BreakReason.Lunch => "Lunch"
    case BreakReason.Coffee => "Coffee"
    case BreakReason.Meeting => "Meeting"
    case BreakReason.Personal => "Personal"
    case BreakReason.Toilet => "Toilet"
    case BreakReason.Other => "Other"
  }

  // ActivityType codec
  private val activityType: Codec[ActivityType] = varchar.imap[ActivityType] {
    case "SessionStart" => ActivityType.SessionStart
    case "SessionEnd" => ActivityType.SessionEnd
    case "BreakStart" => ActivityType.BreakStart
    case "BreakEnd" => ActivityType.BreakEnd
    case "TaskStart" => ActivityType.TaskStart
    case "TaskEnd" => ActivityType.TaskEnd
    case "ModeChange" => ActivityType.ModeChange
    case other => throw new RuntimeException(s"Unknown activity type: $other")
  } {
    case ActivityType.SessionStart => "SessionStart"
    case ActivityType.SessionEnd => "SessionEnd"
    case ActivityType.BreakStart => "BreakStart"
    case ActivityType.BreakEnd => "BreakEnd"
    case ActivityType.TaskStart => "TaskStart"
    case ActivityType.TaskEnd => "TaskEnd"
    case ActivityType.ModeChange => "ModeChange"
  }

  // EnhancedWorkSession codec
  private val sessionCodec: Codec[EnhancedWorkSession] =
    (id *: PeopleSql.id *: localDateTime *: localDateTime.opt *: workMode *:
      bool *: int4 *: int4 *: int4 *: varchar.opt *: varchar.opt *: zonedDateTime)
      .to[EnhancedWorkSession]

  // TimeEntry codec
  private val timeEntryCodec: Codec[TimeEntry] =
    (identification[TimeEntryId] *: PeopleSql.id *: TasksSql.id.opt *: id.opt *:
      localDateTime *: localDateTime.opt *: int4.opt *: varchar *: bool *: bool *:
      breakReason.opt *: bool *: zonedDateTime *: zonedDateTime)
      .to[TimeEntry]

  // TODO: ActivityLog codec - temporarily disabled due to HList issues
  // Will implement after core functionality is working

  // Work Session Queries
  val insertWorkSession: Command[EnhancedWorkSession] =
    sql"""
      INSERT INTO enhanced_work_sessions (
        id, user_id, start_time, end_time, work_mode, is_running,
        total_minutes, break_minutes, productive_minutes,
        description, location, created_at
      ) VALUES ($sessionCodec)
    """.command

  val updateWorkSession: Command[(WorkId, java.time.LocalDateTime, Int, Int, Int)] =
    sql"""
      UPDATE enhanced_work_sessions
      SET
        end_time = $localDateTime,
        is_running = false,
        total_minutes = $int4,
        break_minutes = $int4,
        productive_minutes = $int4
      WHERE id = $id
    """.command.contramap {
      case (sessionId, endTime, totalMin, breakMin, productiveMin) =>
        endTime *: totalMin *: breakMin *: productiveMin *: sessionId *: EmptyTuple
    }

  val findWorkSessionById: Query[WorkId, EnhancedWorkSession] =
    sql"""
      SELECT id, user_id, start_time, end_time, work_mode, is_running,
             total_minutes, break_minutes, productive_minutes,
             description, location, created_at
      FROM enhanced_work_sessions
      WHERE id = $id
      LIMIT 1
    """.query(sessionCodec)

  val findActiveWorkSessions: Query[PersonId, EnhancedWorkSession] =
    sql"""
      SELECT id, user_id, start_time, end_time, work_mode, is_running,
             total_minutes, break_minutes, productive_minutes,
             description, location, created_at
      FROM enhanced_work_sessions
      WHERE user_id = ${PeopleSql.id} AND is_running = true
      ORDER BY start_time DESC
    """.query(sessionCodec)

  val getUserWorkSessions: Query[(PersonId, Int, Int), EnhancedWorkSession] =
    sql"""
      SELECT id, user_id, start_time, end_time, work_mode, is_running,
             total_minutes, break_minutes, productive_minutes,
             description, location, created_at
      FROM enhanced_work_sessions
      WHERE user_id = ${PeopleSql.id}
      ORDER BY start_time DESC
      LIMIT $int4 OFFSET $int4
    """.query(sessionCodec).contramap {
      case (userId, limit, offset) =>
        userId *: limit *: offset *: EmptyTuple
    }

  // Time Entry Queries
  val insertTimeEntry: Command[TimeEntry] =
    sql"""
      INSERT INTO time_entries (
        id, user_id, task_id, work_session_id, start_time, end_time,
        duration, description, is_running, is_break, break_reason,
        is_manual, created_at, updated_at
      ) VALUES ($timeEntryCodec)
    """.command

  val updateTimeEntry: Command[(TimeEntryId, java.time.LocalDateTime, Int)] =
    sql"""
      UPDATE time_entries
      SET
        end_time = $localDateTime,
        duration = $int4,
        is_running = false,
        updated_at = NOW()
      WHERE id = ${identification[TimeEntryId]}
    """.command.contramap {
      case (entryId, endTime, duration) =>
        endTime *: duration *: entryId *: EmptyTuple
    }

  val findTimeEntryById: Query[TimeEntryId, TimeEntry] =
    sql"""
      SELECT id, user_id, task_id, work_session_id, start_time, end_time,
             duration, description, is_running, is_break, break_reason,
             is_manual, created_at, updated_at
      FROM time_entries
      WHERE id = ${identification[TimeEntryId]}
      LIMIT 1
    """.query(timeEntryCodec)

  val findUserTimeEntries: Query[PersonId, TimeEntry] =
    sql"""
      SELECT id, user_id, task_id, work_session_id, start_time, end_time,
             duration, description, is_running, is_break, break_reason,
             is_manual, created_at, updated_at
      FROM time_entries
      WHERE user_id = ${PeopleSql.id}
      ORDER BY start_time DESC
    """.query(timeEntryCodec)

  val findUserTimeEntriesByTask: Query[(PersonId, TaskId), TimeEntry] =
    sql"""
      SELECT id, user_id, task_id, work_session_id, start_time, end_time,
             duration, description, is_running, is_break, break_reason,
             is_manual, created_at, updated_at
      FROM time_entries
      WHERE user_id = ${PeopleSql.id} AND task_id = ${TasksSql.id}
      ORDER BY start_time DESC
    """.query(timeEntryCodec).contramap {
      case (userId, taskId) =>
        userId *: taskId *: EmptyTuple
    }

  val findRunningTimeEntries: Query[PersonId, TimeEntry] =
    sql"""
      SELECT id, user_id, task_id, work_session_id, start_time, end_time,
             duration, description, is_running, is_break, break_reason,
             is_manual, created_at, updated_at
      FROM time_entries
      WHERE user_id = ${PeopleSql.id} AND is_running = true
      ORDER BY start_time DESC
    """.query(timeEntryCodec)
  // TODO: Activity Log Queries - temporarily disabled
  // Will implement after resolving HList codec issues
}
