package tm.domain.corporate

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString

import tm.Phone
import tm.domain.AssetId
import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.enums.Role

case class User(
    id: PersonId,
    createdAt: ZonedDateTime,
    role: Role,
    phone: Phone,
    assetId: Option[AssetId],
    corporateId: CorporateId,
    password: NonEmptyString,
  )
