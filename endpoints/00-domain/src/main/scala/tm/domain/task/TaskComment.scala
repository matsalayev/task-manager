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

case class TaskCommentId(value: java.util.UUID) extends AnyVal

case class TaskComment(
    id: TaskCommentId,
    taskId: TaskId,
    authorId: PersonId,
    content: NonEmptyString,
    parentCommentId: Option[TaskCommentId],
    isEdited: Boolean,
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
  )

case class TaskCommentCreate(
    taskId: TaskId,
    content: NonEmptyString,
    parentCommentId: Option[TaskCommentId],
  )

case class TaskCommentWithAuthor(
    comment: TaskComment,
    authorName: String,
    authorAvatar: Option[String],
    replies: List[TaskCommentWithAuthor] = List.empty,
  )

// Codecs
object TaskCommentId {
  implicit val codec: Codec[TaskCommentId] = deriveCodec
  implicit val isUUID: IsUUID[TaskCommentId] = new IsUUID[TaskCommentId] {
    val uuid: Iso[java.util.UUID, TaskCommentId] = Iso(TaskCommentId(_))(_.value)
  }
}

object TaskComment {
  implicit val codec: Codec[TaskComment] = deriveCodec
}

object TaskCommentCreate {
  implicit val codec: Codec[TaskCommentCreate] = deriveCodec
}

object TaskCommentWithAuthor {
  implicit val codec: Codec[TaskCommentWithAuthor] = deriveCodec
}
