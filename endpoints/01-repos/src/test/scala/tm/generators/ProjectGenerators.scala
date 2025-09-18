package tm.generators

import java.time.ZonedDateTime
import java.util.UUID

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.project.Project

object ProjectGenerators {
  def projectGen: Project = Project(
    id = ProjectId(UUID.randomUUID()),
    createdAt = ZonedDateTime.now(),
    createdBy = PersonId(UUID.randomUUID()),
    corporateId = CorporateId(UUID.randomUUID()),
    name = NonEmptyString.unsafeFrom("Sample Project"),
    description = Some(NonEmptyString.unsafeFrom("Sample project description")),
  )
}
