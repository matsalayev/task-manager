package tm.domain.task

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.CorporateId
import tm.domain.TagId

case class Tag(
    id: TagId,
    name: NonEmptyString,
    color: NonEmptyString,
    corporateId: CorporateId,
  )
