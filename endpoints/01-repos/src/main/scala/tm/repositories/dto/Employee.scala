package tm.repositories.dto

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString

import tm.Phone
import tm.domain.AssetId
import tm.domain.CorporateId
import tm.domain.EmployeeId
import tm.domain.PersonId
import tm.domain.SpecialtyId

case class Employee(
    id: EmployeeId,
    createdAt: ZonedDateTime,
    personId: PersonId,
    fullName: NonEmptyString,
    corporateId: CorporateId,
    corporateName: NonEmptyString,
    specialtyId: SpecialtyId,
    specialtyName: NonEmptyString,
    photo: Option[AssetId],
    phone: Phone,
  )
