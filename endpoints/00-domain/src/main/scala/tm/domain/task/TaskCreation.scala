package tm.domain.task

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.refined._

import tm.domain.AssetId
import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.TagId
import tm.domain.enums.TaskStatus
import tm.syntax.circe._

case class TaskCreation(
    projectId: ProjectId,
    name: NonEmptyString,
    description: Option[NonEmptyString],
    tagId: Option[TagId],
    photo: Option[AssetId],
    status: TaskStatus = TaskStatus.ToDo,
    deadline: Option[ZonedDateTime] = None,
    assignees: List[PersonId] = List.empty,
    link: Option[NonEmptyString] = None,
  )

object TaskCreation {
  implicit val codec: Codec[TaskCreation] = deriveCodec
}

case class TaskUpdate(
    name: Option[NonEmptyString] = None,
    description: Option[NonEmptyString] = None,
    tagId: Option[TagId] = None,
    photo: Option[AssetId] = None,
    status: Option[TaskStatus] = None,
    deadline: Option[ZonedDateTime] = None,
    assignees: Option[List[PersonId]] = None,
    link: Option[NonEmptyString] = None,
  )

object TaskUpdate {
  implicit val codec: Codec[TaskUpdate] = deriveCodec
}

case class TaskAssignment(
    taskId: tm.domain.TaskId,
    assignees: List[PersonId],
  )

object TaskAssignment {
  implicit val codec: Codec[TaskAssignment] = deriveCodec
}
