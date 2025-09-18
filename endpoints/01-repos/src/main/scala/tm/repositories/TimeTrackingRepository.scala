package tm.repositories

import cats.effect.Resource
import cats.effect.kernel.MonadCancelThrow
import skunk.Session

import tm.domain.PersonId
import tm.domain.TaskId
import tm.domain.time.EnhancedWorkSession
import tm.domain.time.TimeEntry

trait TimeTrackingRepository[F[_]] {
  def startWorkSession(session: EnhancedWorkSession): F[EnhancedWorkSession]
  def endWorkSession(sessionId: String): F[Option[EnhancedWorkSession]]
  def getUserActiveSessions(userId: PersonId): F[List[EnhancedWorkSession]]

  def startTimeEntry(entry: TimeEntry): F[TimeEntry]
  def endTimeEntry(entryId: String): F[Option[TimeEntry]]
  def getTimeEntries(userId: PersonId, taskId: Option[TaskId]): F[List[TimeEntry]]
}

object TimeTrackingRepository {
  def make[F[_]: MonadCancelThrow](
      implicit
      session: Resource[F, Session[F]]
    ): TimeTrackingRepository[F] =
    new TimeTrackingRepository[F] {
      def startWorkSession(session: EnhancedWorkSession): F[EnhancedWorkSession] = ???
      def endWorkSession(sessionId: String): F[Option[EnhancedWorkSession]] = ???
      def getUserActiveSessions(userId: PersonId): F[List[EnhancedWorkSession]] = ???

      def startTimeEntry(entry: TimeEntry): F[TimeEntry] = ???
      def endTimeEntry(entryId: String): F[Option[TimeEntry]] = ???
      def getTimeEntries(userId: PersonId, taskId: Option[TaskId]): F[List[TimeEntry]] = ???
    }
}
