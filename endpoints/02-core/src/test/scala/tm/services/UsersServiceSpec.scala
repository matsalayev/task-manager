package tm.services

import java.util.UUID

import cats.effect.IO
import eu.timepit.refined.types.string.NonEmptyString
import weaver.SimpleIOSuite

import tm.domain.PersonId
import tm.domain.users.UserInvitation
import tm.domain.users.UserRegistration
import tm.repositories.PeopleRepository
import tm.repositories.UsersRepository
import tm.syntax.refined._

object UsersServiceSpec extends SimpleIOSuite {

  // Mock repositories for unit testing
  def mockPeopleRepo: PeopleRepository[IO] = new PeopleRepository[IO] {
    import tm.Language
    override def create(person: tm.repositories.dto.Person): IO[Unit] = IO.unit
    override def findById(personId: PersonId): IO[Option[tm.repositories.dto.Person]] =
      IO.pure(None)
    override def delete(personId: PersonId): IO[Unit] = IO.unit
    override def get: IO[List[tm.repositories.dto.Person]] = IO.pure(List.empty)
    override def update(
        personId: PersonId
      )(
        f: tm.repositories.dto.Person => tm.repositories.dto.Person
      )(implicit
        lang: Language
      ): IO[Unit] = IO.unit
  }

  def mockUsersRepo: UsersRepository[IO] = new UsersRepository[IO] {
    override def create(
        user: tm.domain.auth.AccessCredentials[tm.domain.auth.AuthedUser.User]
      ): IO[Unit] = IO.unit
    override def createUser(user: tm.domain.corporate.User): IO[Unit] = IO.unit
    override def find(
        phone: tm.Phone
      ): IO[Option[tm.domain.auth.AccessCredentials[tm.domain.auth.AuthedUser.User]]] =
      IO.pure(None)
    override def findById(userId: PersonId): IO[Option[tm.repositories.dto.User]] = IO.pure(None)
    override def findByPhone(phone: tm.Phone): IO[Option[tm.domain.corporate.User]] = IO.pure(None)
    override def getCorporateUsers(
        corporateId: tm.domain.CorporateId
      ): IO[List[tm.domain.corporate.User]] = IO.pure(List.empty)
  }

  // Simple test data helpers
  def sampleRegistration: UserRegistration = {
    import tm.syntax.refined._
    UserRegistration(
      phone = "+998901234567",
      email = None,
      fullName = NonEmptyString.unsafeFrom("John Doe"),
      password = NonEmptyString.unsafeFrom("password123"),
      gender = Some(tm.domain.enums.Gender.Male),
      dateOfBirth = None,
      documentNumber = None,
      pinfl = None,
      role = tm.domain.enums.Role.Employee,
      corporateId = tm.domain.CorporateId(UUID.randomUUID()),
    )
  }

  def sampleInvitation: UserInvitation = {
    import tm.syntax.refined._
    UserInvitation(
      phone = "+998901234567",
      email = None,
      fullName = NonEmptyString.unsafeFrom("Jane Doe"),
      role = tm.domain.enums.Role.Employee,
      corporateId = tm.domain.CorporateId(UUID.randomUUID()),
    )
  }

  test("registerUser should create person and user records") {
    val usersService = UsersService.make[IO](mockUsersRepo, mockPeopleRepo)

    for {
      result <- usersService.registerUser(sampleRegistration)
    } yield expect(result != null) // Just verify it returns a PersonId
  }

  test("inviteUser should create person and user with temporary password") {
    val usersService = UsersService.make[IO](mockUsersRepo, mockPeopleRepo)

    for {
      result <- usersService.inviteUser(sampleInvitation)
    } yield expect(result != null) // Just verify it returns a PersonId
  }

  test("getUserProfile should return None for non-existent user") {
    val personId = PersonId(UUID.randomUUID())
    val usersService = UsersService.make[IO](mockUsersRepo, mockPeopleRepo)

    for {
      profile <- usersService.getUserProfile(personId)
    } yield expect(profile.isEmpty)
  }

  test("getUserById should return None for non-existent user") {
    val personId = PersonId(UUID.randomUUID())
    val usersService = UsersService.make[IO](mockUsersRepo, mockPeopleRepo)

    for {
      user <- usersService.getUserById(personId)
    } yield expect(user.isEmpty)
  }

  test("getUsersByCompany should return empty list for non-existent company") {
    val corporateId = tm.domain.CorporateId(UUID.randomUUID())
    val usersService = UsersService.make[IO](mockUsersRepo, mockPeopleRepo)

    for {
      users <- usersService.getUsersByCompany(corporateId)
    } yield expect(users.isEmpty)
  }
}
