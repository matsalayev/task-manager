package tm.repositories

import cats.effect.IO
import cats.effect.Resource
import cats.implicits.catsSyntaxOptionId
import skunk.Session

import tm.Language
import tm.database.DBSuite
import tm.generators.Generators
import tm.generators.PeopleGenerators
import tm.syntax.refined._

object PeopleRepositorySpec extends DBSuite with Generators with PeopleGenerators {
  override def schemaName: String = "public"
  override def beforeAll(implicit session: Resource[IO, Session[IO]]): IO[Unit] = data.setup

  test("Create person") { implicit postgres =>
    PeopleRepository
      .make[F]
      .create(personGen.gen)
      .map(_ => success)
      .handleError(_ => failure("error"))

  }

  test("Find person by id") { implicit postgres =>
    val peopleRepo = PeopleRepository.make[F]
    for {
      person <- peopleRepo.findById(data.people.person1.id)
    } yield assert(person.exists(_.id == data.people.person1.id))
  }

  test("Delete person by id") { implicit postgres =>
    val person: dto.Person = personGen
    val peopleRepo = PeopleRepository.make[F]
    for {
      _ <- peopleRepo.create(person)
      _ <- peopleRepo.delete(person.id)
      deletedPerson <- peopleRepo.findById(person.id)
    } yield assert(deletedPerson.isEmpty)
  }

  test("Update person by id") { implicit postgres =>
    val gender = genderGen.gen
    val dateOfBirth = dateGen.gen
    val fullName = nonEmptyString.gen
    val peopleRepo = PeopleRepository.make[F]
    implicit val lang: Language = Language.En
    for {
      _ <- peopleRepo.update(data.people.person1.id)(
        _.copy(fullName = fullName, gender = gender, dateOfBirth = dateOfBirth.some)
      )
      person <- peopleRepo.findById(data.people.person1.id)
    } yield assert(
      person.exists(p =>
        p.fullName.value == fullName && p.gender == gender && p.dateOfBirth.contains(dateOfBirth)
      )
    )
  }
}
