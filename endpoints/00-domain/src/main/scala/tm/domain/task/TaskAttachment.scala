package tm.domain.task

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.refined._
import monocle.Iso

import tm.domain.PersonId
import tm.domain.TaskId
import tm.effects.IsUUID
import tm.syntax.circe._

case class TaskAttachmentId(value: java.util.UUID) extends AnyVal

case class TaskAttachment(
    id: TaskAttachmentId,
    taskId: TaskId,
    fileName: NonEmptyString,
    filePath: NonEmptyString,
    fileSize: Long,
    mimeType: Option[NonEmptyString],
    uploadedBy: PersonId,
    uploadedAt: ZonedDateTime,
  )

case class TaskAttachmentCreate(
    taskId: TaskId,
    fileName: NonEmptyString,
    filePath: NonEmptyString,
    fileSize: Long,
    mimeType: Option[NonEmptyString],
  )

// Codecs
object TaskAttachmentId {
  implicit val codec: Codec[TaskAttachmentId] = deriveCodec
  implicit val isUUID: IsUUID[TaskAttachmentId] = new IsUUID[TaskAttachmentId] {
    val uuid: Iso[java.util.UUID, TaskAttachmentId] = Iso(TaskAttachmentId(_))(_.value)
  }
}

object TaskAttachment {
  implicit val codec: Codec[TaskAttachment] = deriveCodec
}

object TaskAttachmentCreate {
  implicit val codec: Codec[TaskAttachmentCreate] = deriveCodec
}
