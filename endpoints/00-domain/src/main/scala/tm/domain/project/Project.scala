package tm.domain.project

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.CorporateId
import tm.domain.EmployeeId
import tm.domain.ProjectId

case class Project(
    id: ProjectId,
    createdAt: ZonedDateTime,
    createdBy: EmployeeId,
    corporateId: CorporateId,
    name: NonEmptyString,
    description: Option[NonEmptyString],
  )
