package tm.generators

import eu.timepit.refined.types.string.NonEmptyString
import org.scalacheck.Gen

import tm.domain.PersonId
import tm.repositories.dto
import tm.syntax.refined._

trait PeopleGenerators { this: Generators =>
  def personGen(personId: PersonId): Gen[dto.Person] =
    for {
      createdAt <- zonedDateTimeGen
      dateOfBirth <- dateGen.opt
      gender <- genderGen
      fullName <- nonEmptyString.map(NonEmptyString.unsafeFrom)
    } yield dto.Person(
      id = personId,
      createdAt = createdAt,
      fullName = fullName,
      gender = gender,
      dateOfBirth = dateOfBirth,
      documentNumber = None,
      pinflNumber = None,
      updatedAt = None,
      deletedAt = None,
    )
}
