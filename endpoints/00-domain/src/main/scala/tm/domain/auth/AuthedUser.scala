package tm.domain.auth

import io.circe.generic.JsonCodec
import io.circe.refined._

import tm.Phone
import tm.domain.PersonId
import tm.domain.enums.Role
import tm.syntax.circe._

@JsonCodec
sealed trait AuthedUser {
  val id: PersonId
  val role: Role
  val phone: Phone
}

object AuthedUser {
  @JsonCodec
  case class User(
      id: PersonId,
      role: Role,
      phone: Phone,
    ) extends AuthedUser
}
