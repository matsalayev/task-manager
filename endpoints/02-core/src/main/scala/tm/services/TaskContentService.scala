package tm.services

import cats.effect.kernel.MonadCancelThrow
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.PersonId
import tm.domain.TaskId
import tm.domain.task.TaskAttachment
import tm.domain.task.TaskComment
import tm.repositories.TaskContentRepository
import tm.repositories.TasksRepository
import tm.syntax.refined._

trait TaskContentService[F[_]] {
  def addComment(
      taskId: TaskId,
      content: String,
      authorId: PersonId,
    ): F[TaskComment]
  def getComments(taskId: TaskId, userId: PersonId): F[List[TaskComment]]
  def addAttachment(
      taskId: TaskId,
      fileName: String,
      filePath: String,
      fileSize: Long,
      mimeType: Option[String],
      userId: PersonId,
    ): F[TaskAttachment]
  def getAttachments(taskId: TaskId, userId: PersonId): F[List[TaskAttachment]]
}

object TaskContentService {
  def make[F[_]: MonadCancelThrow](
      contentRepository: TaskContentRepository[F],
      tasksRepository: TasksRepository[F],
    ): TaskContentService[F] =
    new TaskContentService[F] {
      def addComment(
          taskId: TaskId,
          content: String,
          authorId: PersonId,
        ): F[TaskComment] = {
        import tm.domain.task.TaskCommentId
        val comment = TaskComment(
          id = TaskCommentId(java.util.UUID.randomUUID()),
          taskId = taskId,
          authorId = authorId,
          content = content,
          parentCommentId = None,
          isEdited = false,
          createdAt = java.time.ZonedDateTime.now(),
          updatedAt = java.time.ZonedDateTime.now(),
        )
        contentRepository.addComment(comment)
      }

      def getComments(taskId: TaskId, userId: PersonId): F[List[TaskComment]] =
        contentRepository.getComments(taskId)

      def addAttachment(
          taskId: TaskId,
          fileName: String,
          filePath: String,
          fileSize: Long,
          mimeType: Option[String],
          userId: PersonId,
        ): F[TaskAttachment] = {
        import tm.domain.task.TaskAttachmentId
        val attachment = TaskAttachment(
          id = TaskAttachmentId(java.util.UUID.randomUUID()),
          taskId = taskId,
          fileName = fileName,
          filePath = filePath,
          fileSize = fileSize,
          mimeType = mimeType.map(s => s: NonEmptyString),
          uploadedBy = userId,
          uploadedAt = java.time.ZonedDateTime.now(),
        )
        contentRepository.addAttachment(attachment)
      }

      def getAttachments(taskId: TaskId, userId: PersonId): F[List[TaskAttachment]] =
        contentRepository.getAttachments(taskId)
    }
}
