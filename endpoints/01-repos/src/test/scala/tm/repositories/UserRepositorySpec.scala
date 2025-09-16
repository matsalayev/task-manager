package tm.repositories

import cats.effect.IO
import eu.timepit.refined.types.string.NonEmptyString

import tm.database.DBSuite
import tm.domain.LocationId
import tm.domain.auth.AccessCredentials
import tm.domain.auth.AuthedUser.User
import tm.domain.corporate.Corporate
import tm.domain.corporate.Location
import tm.generators.Generators
import tm.generators.PeopleGenerators
import tm.generators.UserGenerators

object UserRepositorySpec
    extends DBSuite
       with Generators
       with UserGenerators
       with PeopleGenerators {
  override def schemaName: String = "public"

  private def setupCorporateAndLocation(
      corporateId: tm.domain.CorporateId
    )(implicit
      session: Res
    ): IO[Unit] = {
    val locationId = LocationId(java.util.UUID.randomUUID())
    val location = Location(
      id = locationId,
      name = NonEmptyString.unsafeFrom("Test Location"),
      latitude = 41.2995,
      longitude = 69.2401,
    )
    val corporate = Corporate(
      id = corporateId,
      createdAt = java.time.ZonedDateTime.now(),
      name = NonEmptyString.unsafeFrom("Test Corporation"),
      locationId = locationId,
      photo = None,
    )

    val corporationsRepo = CorporationsRepository.make[IO]
    for {
      _ <- corporationsRepo.createLocation(location)
      _ <- corporationsRepo.create(corporate)
    } yield ()
  }

  test("create and find user by phone") { implicit session =>
    val personId = personIdGen.gen
    val person = personGen(personId).gen
    val userWithHash: AccessCredentials[User] = createUserGen(personId).gen

    val peopleRepo = PeopleRepository.make[IO]
    val usersRepo = UsersRepository.make[IO]

    for {
      // Setup corporate and location
      _ <- setupCorporateAndLocation(userWithHash.data.corporateId)
      // Create person record
      _ <- peopleRepo.create(person)
      // Create user record
      _ <- usersRepo.create(userWithHash)
      // Find user by phone
      foundUser <- usersRepo.find(userWithHash.data.phone)
    } yield expect(foundUser.isDefined) and
      expect(foundUser.get.data.phone == userWithHash.data.phone) and
      expect(foundUser.get.data.id == personId)
  }

  test("find user by ID") { implicit session =>
    val personId = personIdGen.gen
    val person = personGen(personId).gen
    val userWithHash: AccessCredentials[User] = createUserGen(personId).gen

    val peopleRepo = PeopleRepository.make[IO]
    val usersRepo = UsersRepository.make[IO]

    for {
      _ <- setupCorporateAndLocation(userWithHash.data.corporateId)
      _ <- peopleRepo.create(person)
      _ <- usersRepo.create(userWithHash)
      foundUser <- usersRepo.findById(personId)
    } yield expect(foundUser.isDefined) and
      expect(foundUser.get.id == personId) and
      expect(foundUser.get.phone == userWithHash.data.phone)
  }

  test("find user by phone (corporate user)") { implicit session =>
    val personId = personIdGen.gen
    val person = personGen(personId).gen
    val userWithHash: AccessCredentials[User] = createUserGen(personId).gen

    val peopleRepo = PeopleRepository.make[IO]
    val usersRepo = UsersRepository.make[IO]

    for {
      _ <- setupCorporateAndLocation(userWithHash.data.corporateId)
      _ <- peopleRepo.create(person)
      _ <- usersRepo.create(userWithHash)
      foundUser <- usersRepo.findByPhone(userWithHash.data.phone)
    } yield expect(foundUser.isDefined) and
      expect(foundUser.get.phone == userWithHash.data.phone) and
      expect(foundUser.get.id == personId)
  }

  test("get corporate users by corporate ID") { implicit session =>
    val corporateId = idGen(tm.domain.CorporateId.apply).gen
    val personId1 = personIdGen.gen
    val personId2 = personIdGen.gen
    val person1 = personGen(personId1).gen
    val person2 = personGen(personId2).gen

    // Create users with same corporate ID
    val user1Data = createUserGen(personId1).gen.data.copy(corporateId = corporateId)
    val user2Data = createUserGen(personId2).gen.data.copy(corporateId = corporateId)
    val user1 = AccessCredentials(user1Data, createUserGen(personId1).gen.password)
    val user2 = AccessCredentials(user2Data, createUserGen(personId2).gen.password)

    val peopleRepo = PeopleRepository.make[IO]
    val usersRepo = UsersRepository.make[IO]

    for {
      _ <- setupCorporateAndLocation(corporateId)
      _ <- peopleRepo.create(person1)
      _ <- peopleRepo.create(person2)
      _ <- usersRepo.create(user1)
      _ <- usersRepo.create(user2)
      users <- usersRepo.getCorporateUsers(corporateId)
    } yield expect(users.length >= 2) and
      expect(users.exists(_.id == personId1)) and
      expect(users.exists(_.id == personId2))
  }

  test("user not found scenarios") { implicit session =>
    val usersRepo = UsersRepository.make[IO]
    val nonExistentPhone = phoneGen.gen
    val nonExistentId = personIdGen.gen

    for {
      userByPhone <- usersRepo.find(nonExistentPhone)
      userById <- usersRepo.findById(nonExistentId)
      corporateUser <- usersRepo.findByPhone(nonExistentPhone)
    } yield expect(userByPhone.isEmpty) and
      expect(userById.isEmpty) and
      expect(corporateUser.isEmpty)
  }
}
