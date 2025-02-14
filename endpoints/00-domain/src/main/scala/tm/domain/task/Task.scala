package tm.domain.task

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.AssetId
import tm.domain.EmployeeId
import tm.domain.ProjectId
import tm.domain.TagId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus

case class Task(
    id: TaskId,
    createdAt: ZonedDateTime,
    createdBy: EmployeeId,
    projectId: ProjectId,
    name: NonEmptyString,
    description: Option[NonEmptyString],
    tagId: Option[TagId],
    photo: Option[AssetId],
    status: TaskStatus,
    deadline: Option[ZonedDateTime],
  )
