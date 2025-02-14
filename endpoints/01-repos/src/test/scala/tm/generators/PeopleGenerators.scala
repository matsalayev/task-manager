package tm.generators

import org.scalacheck.Gen

import tm.repositories.dto
import tm.syntax.refined._

trait PeopleGenerators { this: Generators =>
  def personGen: Gen[dto.Person] =
    for {
      id <- personIdGen
      createdAt <- zonedDateTimeGen
      dateOfBirth <- dateGen.opt
      gender <- genderGen
      fullName <- nonEmptyString
    } yield dto.Person(
      id = id,
      createdAt = createdAt,
      dateOfBirth = dateOfBirth,
      gender = gender,
      fullName = fullName,
      documentNumber = None,
      pinflNumber = None,
      updatedAt = None,
      deletedAt = None,
    )
}
