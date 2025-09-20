package tm.repositories

import java.time.LocalDateTime

import cats.effect.Resource
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import skunk.Session

import tm.domain.PersonId
import tm.domain.TaskId
import tm.domain.WorkId
import tm.domain.time._
import tm.repositories.sql.TimeTrackingSql
import tm.support.skunk.syntax.all._

trait TimeTrackingRepository[F[_]] {
  def startWorkSession(session: EnhancedWorkSession): F[EnhancedWorkSession]
  def endWorkSession(
      sessionId: WorkId,
      endTime: LocalDateTime,
      totalMinutes: Int,
      breakMinutes: Int,
      productiveMinutes: Int,
    ): F[Option[EnhancedWorkSession]]
  def findWorkSessionById(sessionId: WorkId): F[Option[EnhancedWorkSession]]
  def getUserActiveSessions(userId: PersonId): F[List[EnhancedWorkSession]]
  def getUserWorkSessions(
      userId: PersonId,
      limit: Int,
      offset: Int,
    ): F[List[EnhancedWorkSession]]

  def startTimeEntry(entry: TimeEntry): F[TimeEntry]
  def endTimeEntry(
      entryId: TimeEntryId,
      endTime: LocalDateTime,
      duration: Int,
    ): F[Option[TimeEntry]]
  def findTimeEntryById(entryId: TimeEntryId): F[Option[TimeEntry]]
  def getUserTimeEntries(userId: PersonId, taskId: Option[TaskId]): F[List[TimeEntry]]
  def getUserRunningTimeEntries(userId: PersonId): F[List[TimeEntry]]

  def logActivity(log: ActivityLog): F[Unit]
  def getUserActivityLogs(
      userId: PersonId,
      limit: Int,
      offset: Int,
    ): F[List[ActivityLog]]
}

object TimeTrackingRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): TimeTrackingRepository[F] =
    new TimeTrackingRepository[F] {
      override def startWorkSession(workSession: EnhancedWorkSession): F[EnhancedWorkSession] =
        TimeTrackingSql.insertWorkSession.execute(workSession).as(workSession)

      override def endWorkSession(
          sessionId: WorkId,
          endTime: LocalDateTime,
          totalMinutes: Int,
          breakMinutes: Int,
          productiveMinutes: Int,
        ): F[Option[EnhancedWorkSession]] =
        for {
          _ <- TimeTrackingSql
            .updateWorkSession
            .execute((sessionId, endTime, totalMinutes, breakMinutes, productiveMinutes))
          updated <- TimeTrackingSql.findWorkSessionById.queryOption(sessionId)
        } yield updated

      override def findWorkSessionById(sessionId: WorkId): F[Option[EnhancedWorkSession]] =
        TimeTrackingSql.findWorkSessionById.queryOption(sessionId)

      override def getUserActiveSessions(userId: PersonId): F[List[EnhancedWorkSession]] =
        TimeTrackingSql.findActiveWorkSessions.queryList(userId)

      override def getUserWorkSessions(
          userId: PersonId,
          limit: Int,
          offset: Int,
        ): F[List[EnhancedWorkSession]] =
        TimeTrackingSql.getUserWorkSessions.queryList((userId, limit, offset))

      override def startTimeEntry(entry: TimeEntry): F[TimeEntry] =
        TimeTrackingSql.insertTimeEntry.execute(entry).as(entry)

      override def endTimeEntry(
          entryId: TimeEntryId,
          endTime: LocalDateTime,
          duration: Int,
        ): F[Option[TimeEntry]] =
        for {
          _ <- TimeTrackingSql.updateTimeEntry.execute((entryId, endTime, duration))
          updated <- TimeTrackingSql.findTimeEntryById.queryOption(entryId)
        } yield updated

      override def findTimeEntryById(entryId: TimeEntryId): F[Option[TimeEntry]] =
        TimeTrackingSql.findTimeEntryById.queryOption(entryId)

      override def getUserTimeEntries(
          userId: PersonId,
          taskId: Option[TaskId],
        ): F[List[TimeEntry]] =
        taskId match {
          case Some(tid) => TimeTrackingSql.findUserTimeEntriesByTask.queryList((userId, tid))
          case None => TimeTrackingSql.findUserTimeEntries.queryList(userId)
        }

      override def getUserRunningTimeEntries(userId: PersonId): F[List[TimeEntry]] =
        TimeTrackingSql.findRunningTimeEntries.queryList(userId)

      override def logActivity(log: ActivityLog): F[Unit] =
        ().pure[F] // TODO: Implement after fixing ActivityLog codec

      override def getUserActivityLogs(
          userId: PersonId,
          limit: Int,
          offset: Int,
        ): F[List[ActivityLog]] =
        List.empty[ActivityLog].pure[F] // TODO: Implement after fixing ActivityLog codec
    }
}
