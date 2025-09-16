package tm.generators

import eu.timepit.refined.types.string.NonEmptyString
import org.scalacheck.Gen
import tsec.passwordhashers.jca.SCrypt

import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.auth.AccessCredentials
import tm.domain.auth.AuthedUser.User
import tm.domain.users.UserRegistration
import tm.repositories.dto

trait UserGenerators { this: Generators =>
  def createUserGen(personId: PersonId): Gen[AccessCredentials[User]] =
    for {
      role <- roleGen
      phone <- phoneGen
      corporateId <- idGen(CorporateId.apply)
    } yield AccessCredentials[User](
      data = User(
        id = personId,
        corporateId = corporateId,
        role = role,
        phone = phone,
      ),
      password = SCrypt.hashpwUnsafe(phone.value),
    )

  def userRegistrationGen: Gen[UserRegistration] =
    for {
      phone <- phoneGen
      email <- Gen.option(emailGen)
      fullName <- nonEmptyStringGen().map(NonEmptyString.unsafeFrom)
      password <- nonEmptyStringGen(8, 20).map(NonEmptyString.unsafeFrom)
      role <- roleGen
      corporateId <- idGen(CorporateId.apply)
      gender <- Gen.option(genderGen)
      dateOfBirth <- Gen.option(dateGen)
      documentNumber <- Gen.option(nonEmptyStringGen().map(NonEmptyString.unsafeFrom))
      pinfl <- Gen.option(nonEmptyStringGen(14, 14).map(NonEmptyString.unsafeFrom))
    } yield UserRegistration(
      phone = phone,
      email = email,
      fullName = fullName,
      password = password,
      role = role,
      corporateId = corporateId,
      gender = gender,
      dateOfBirth = dateOfBirth,
      documentNumber = documentNumber,
      pinfl = pinfl,
    )

  def dtoUserGen: Gen[dto.User] =
    for {
      id <- idGen(PersonId.apply)
      createdAt <- zonedDateTimeGen
      fullName <- nonEmptyStringGen().map(NonEmptyString.unsafeFrom)
      corporateId <- idGen(CorporateId.apply)
      corporateName <- nonEmptyStringGen().map(NonEmptyString.unsafeFrom)
      role <- roleGen
      phone <- phoneGen
    } yield dto.User(
      id = id,
      createdAt = createdAt,
      fullName = fullName,
      corporateId = corporateId,
      corporateName = corporateName,
      role = role,
      photo = None,
      phone = phone,
    )
}
