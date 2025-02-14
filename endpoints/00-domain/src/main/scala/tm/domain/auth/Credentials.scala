package tm.domain.auth

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.JsonCodec
import io.circe.refined._
import tm.Phone

@JsonCodec
case class Credentials(phone: Phone, password: NonEmptyString)
