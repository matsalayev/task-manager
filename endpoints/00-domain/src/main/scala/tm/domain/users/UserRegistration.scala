package tm.domain.users

import java.time.LocalDate

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.JsonCodec
import io.circe.refined._

import tm.Email
import tm.Phone
import tm.domain.CorporateId
import tm.domain.enums.Gender
import tm.domain.enums.Role
import tm.syntax.circe._

@JsonCodec
case class UserRegistration(
    phone: Phone,
    email: Option[Email],
    fullName: NonEmptyString,
    password: NonEmptyString,
    role: Role,
    corporateId: CorporateId,
    gender: Option[Gender] = None,
    dateOfBirth: Option[LocalDate] = None,
    documentNumber: Option[NonEmptyString] = None,
    pinfl: Option[NonEmptyString] = None,
  )

@JsonCodec
case class UserInvitation(
    phone: Phone,
    email: Option[Email],
    fullName: NonEmptyString,
    role: Role,
    corporateId: CorporateId,
  )

@JsonCodec
case class UserProfile(
    fullName: NonEmptyString,
    email: Option[Email],
    gender: Option[Gender],
    dateOfBirth: Option[LocalDate],
    documentNumber: Option[NonEmptyString],
    pinfl: Option[NonEmptyString],
  )

@JsonCodec
case class UserProfileUpdate(
    fullName: Option[NonEmptyString] = None,
    email: Option[Email] = None,
    gender: Option[Gender] = None,
    dateOfBirth: Option[LocalDate] = None,
    documentNumber: Option[NonEmptyString] = None,
    pinfl: Option[NonEmptyString] = None,
  )

@JsonCodec
case class PasswordChange(
    currentPassword: NonEmptyString,
    newPassword: NonEmptyString,
  )
