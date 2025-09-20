package tm.repositories.dto

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.JsonCodec
import io.circe.refined._

import tm.Phone
import tm.domain.AssetId
import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.enums.Role
import tm.syntax.circe._

@JsonCodec
case class User(
    id: PersonId,
    createdAt: ZonedDateTime,
    fullName: NonEmptyString,
    corporateId: CorporateId,
    corporateName: NonEmptyString,
    role: Role,
    photo: Option[AssetId],
    phone: Phone,
  )
