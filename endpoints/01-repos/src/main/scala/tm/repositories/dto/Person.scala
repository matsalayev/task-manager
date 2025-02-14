package tm.repositories.dto

import java.time.LocalDate
import java.time.ZonedDateTime

import cats.implicits.catsSyntaxOptionId
import eu.timepit.refined.types.string.NonEmptyString
import io.scalaland.chimney.dsl.TransformationOps
import tm.domain.PersonId
import tm.domain.enums.Gender

case class Person(
    id: PersonId,
    createdAt: ZonedDateTime,
    fullName: NonEmptyString,
    gender: Gender,
    dateOfBirth: Option[LocalDate],
    documentNumber: Option[NonEmptyString],
    pinflNumber: Option[NonEmptyString],
    updatedAt: Option[ZonedDateTime],
    deletedAt: Option[ZonedDateTime],
  )
