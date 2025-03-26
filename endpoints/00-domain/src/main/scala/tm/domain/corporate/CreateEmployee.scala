package tm.domain.corporate

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.JsonCodec
import io.circe.refined._

import tm.Phone
import tm.domain.enums.Gender
import tm.syntax.refined._

@JsonCodec
case class CreateEmployee(
    name: NonEmptyString,
    gender: Gender,
    phone: Phone,
  )
