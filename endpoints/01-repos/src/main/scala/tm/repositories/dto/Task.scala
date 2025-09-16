package tm.repositories.dto

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.refined._

import tm.domain.AssetId
import tm.domain.ProjectId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus
import tm.syntax.circe._

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
    link: Option[NonEmptyString],
  )

object Task {
  implicit val codec: Codec[Task] = deriveCodec
}
