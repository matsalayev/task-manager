package tm.domain.lite

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.FolderId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus

case class LiteTask(
    id: TaskId,
    createdAt: ZonedDateTime,
    userId: Long,
    folderId: FolderId,
    name: NonEmptyString,
    status: TaskStatus,
    startedAt: Option[ZonedDateTime],
    finishedAt: Option[ZonedDateTime],
    duration: Long,
  )
