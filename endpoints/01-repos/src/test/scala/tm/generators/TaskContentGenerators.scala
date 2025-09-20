package tm.generators

import java.time.ZonedDateTime
import java.util.UUID

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.PersonId
import tm.domain.TaskId
import tm.domain.task._

object TaskContentGenerators {
  def taskCommentGen: TaskComment = TaskComment(
    id = TaskCommentId(UUID.randomUUID()),
    taskId = TaskId(UUID.randomUUID()),
    authorId = PersonId(UUID.randomUUID()),
    content = NonEmptyString.unsafeFrom("Sample comment"),
    parentCommentId = None,
    isEdited = false,
    createdAt = ZonedDateTime.now(),
    updatedAt = ZonedDateTime.now(),
  )

  def taskCommentCreateGen: TaskCommentCreate = TaskCommentCreate(
    taskId = TaskId(UUID.randomUUID()),
    content = NonEmptyString.unsafeFrom("Sample comment content"),
    parentCommentId = None,
  )

  def taskAttachmentGen: TaskAttachment = TaskAttachment(
    id = TaskAttachmentId(UUID.randomUUID()),
    taskId = TaskId(UUID.randomUUID()),
    fileName = NonEmptyString.unsafeFrom("test.pdf"),
    filePath = NonEmptyString.unsafeFrom("/uploads/test.pdf"),
    fileSize = 1024L,
    mimeType = Some(NonEmptyString.unsafeFrom("application/pdf")),
    uploadedBy = PersonId(UUID.randomUUID()),
    uploadedAt = ZonedDateTime.now(),
  )

  def taskAttachmentCreateGen: TaskAttachmentCreate = TaskAttachmentCreate(
    taskId = TaskId(UUID.randomUUID()),
    fileName = NonEmptyString.unsafeFrom("test.pdf"),
    filePath = NonEmptyString.unsafeFrom("/uploads/test.pdf"),
    fileSize = 1024L,
    mimeType = Some(NonEmptyString.unsafeFrom("application/pdf")),
  )
}
