package tm.repositories.dto

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.AssetId
import tm.domain.ProjectId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus

case class Task(
    id: TaskId,
    createdAt: ZonedDateTime,
    createdBy: NonEmptyString,
    projectId: ProjectId,
    projectName: NonEmptyString,
    name: NonEmptyString,
    description: Option[NonEmptyString],
    tagName: Option[NonEmptyString],
    tagColor: Option[NonEmptyString],
    assetId: Option[AssetId],
    status: TaskStatus,
    deadline: Option[ZonedDateTime],
  )
