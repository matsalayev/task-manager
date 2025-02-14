package tm.domain

import java.time.LocalDate
import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.JsonCodec
import io.circe.refined._

import tm.domain.enums.Gender
import tm.syntax.circe._

@JsonCodec
case class Person(
    id: PersonId,
    createdAt: ZonedDateTime,
    fullName: NonEmptyString,
    gender: Gender,
    dateOfBirth: Option[LocalDate],
    documentNumber: Option[NonEmptyString],
    pinfl: Option[NonEmptyString],
  )
