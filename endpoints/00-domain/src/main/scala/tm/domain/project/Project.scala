package tm.domain.project

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.refined._

import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.ProjectId
import tm.syntax.circe._

case class Project(
    id: ProjectId,
    createdAt: ZonedDateTime,
    createdBy: PersonId,
    corporateId: CorporateId,
    name: NonEmptyString,
    description: Option[NonEmptyString],
  )

object Project {
  implicit val codec: Codec[Project] = deriveCodec
}
