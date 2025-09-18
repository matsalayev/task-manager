package tm.domain.corporate

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.refined._

import tm.Phone
import tm.domain.AssetId
import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.enums.Role
import tm.syntax.circe._

case class User(
    id: PersonId,
    createdAt: ZonedDateTime,
    role: Role,
    phone: Phone,
    assetId: Option[AssetId],
    corporateId: CorporateId,
    password: NonEmptyString,
  )

object User {
  implicit val codec: Codec[User] = deriveCodec
}
