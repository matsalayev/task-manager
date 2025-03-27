package tm.domain.project

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.JsonCodec
import io.circe.refined._

import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.ProjectId
import tm.syntax.refined._

@JsonCodec
case class Project(
    id: ProjectId,
    createdAt: ZonedDateTime,
    createdBy: PersonId,
    corporateId: CorporateId,
    name: NonEmptyString,
    description: Option[NonEmptyString],
  )
