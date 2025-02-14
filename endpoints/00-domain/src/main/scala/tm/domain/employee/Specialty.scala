package tm.domain.employee

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.SpecialtyId

case class Specialty(
    id: SpecialtyId,
    name: NonEmptyString,
  )
