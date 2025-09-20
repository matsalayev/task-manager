package tm.repositories

import cats.effect.Resource
import cats.effect.kernel.MonadCancelThrow
import skunk.Session

import tm.domain.PersonId
import tm.domain.TaskId
import tm.domain.task.TaskAttachment
import tm.domain.task.TaskComment

trait TaskContentRepository[F[_]] {
  def addComment(comment: TaskComment): F[TaskComment]
  def getComments(taskId: TaskId): F[List[TaskComment]]
  def updateComment(commentId: TaskId, content: String): F[Option[TaskComment]]
  def deleteComment(commentId: TaskId): F[Unit]

  def addAttachment(attachment: TaskAttachment): F[TaskAttachment]
  def getAttachments(taskId: TaskId): F[List[TaskAttachment]]
  def deleteAttachment(attachmentId: TaskId): F[Unit]
}

object TaskContentRepository {
  def make[F[_]: MonadCancelThrow](
      implicit
      session: Resource[F, Session[F]]
    ): TaskContentRepository[F] =
    new TaskContentRepository[F] {
      def addComment(comment: TaskComment): F[TaskComment] = ???
      def getComments(taskId: TaskId): F[List[TaskComment]] = ???
      def updateComment(commentId: TaskId, content: String): F[Option[TaskComment]] = ???
      def deleteComment(commentId: TaskId): F[Unit] = ???

      def addAttachment(attachment: TaskAttachment): F[TaskAttachment] = ???
      def getAttachments(taskId: TaskId): F[List[TaskAttachment]] = ???
      def deleteAttachment(attachmentId: TaskId): F[Unit] = ???
    }
}
