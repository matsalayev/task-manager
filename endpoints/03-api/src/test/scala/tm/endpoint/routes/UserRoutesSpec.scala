package tm.endpoint.routes

import java.util.UUID

import cats.effect.IO
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import weaver.SimpleIOSuite

import tm.domain.PersonId
import tm.domain.auth.AuthedUser
import tm.domain.users.UserInvitation
import tm.domain.users.UserRegistration
import tm.services.UsersService
import tm.syntax.refined._

object UserRoutesSpec extends SimpleIOSuite {

  // Mock services for testing
  def mockUsersService: UsersService[IO] = new UsersService[IO] {
    override def registerUser(registration: UserRegistration): IO[PersonId] =
      IO.pure(PersonId(UUID.randomUUID()))

    override def inviteUser(invitation: UserInvitation): IO[PersonId] =
      IO.pure(PersonId(UUID.randomUUID()))

    override def getUserProfile(userId: PersonId): IO[Option[tm.domain.users.UserProfile]] =
      IO.pure(None)

    override def updateUserProfile(
        userId: PersonId,
        update: tm.domain.users.UserProfileUpdate,
      ): IO[Unit] =
      IO.unit

    override def changePassword(
        userId: PersonId,
        passwordChange: tm.domain.users.PasswordChange,
      ): IO[Unit] =
      IO.unit

    override def deleteUser(userId: PersonId): IO[Unit] =
      IO.unit

    override def getUserById(userId: PersonId): IO[Option[tm.repositories.dto.User]] =
      IO.pure(None)

    override def getUsersByCompany(
        corporateId: tm.domain.CorporateId
      ): IO[List[tm.repositories.dto.User]] =
      IO.pure(List.empty)

    override def getUserByPhone(phone: tm.Phone): IO[Option[tm.repositories.dto.User]] =
      IO.pure(None)
  }

  def sampleUserRegistration: UserRegistration = {
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

  def sampleUserInvitation: UserInvitation = {
    import tm.syntax.refined._
    UserInvitation(
      phone = "+998901234567",
      email = None,
      fullName = NonEmptyString.unsafeFrom("Jane Doe"),
      role = tm.domain.enums.Role.Employee,
      corporateId = tm.domain.CorporateId(UUID.randomUUID()),
    )
  }

  implicit val logger: org.typelevel.log4cats.Logger[IO] =
    org.typelevel.log4cats.noop.NoOpLogger.impl[IO]

  test("POST /users/register should create a new user") {
    val routes = UserRoutes[IO](mockUsersService)
    val request = Request[IO](
      method = Method.POST,
      uri = uri"/register",
    ).withEntity(sampleUserRegistration)

    for {
      response <- routes.public.orNotFound.run(request)
    } yield expect(response.status == Status.Created)
  }

  test("POST /users/invite should invite a new user") {
    val routes = UserRoutes[IO](mockUsersService)
    val request = Request[IO](
      method = Method.POST,
      uri = uri"/invite",
    ).withEntity(sampleUserInvitation)

    for {
      response <- routes.public.orNotFound.run(request)
    } yield expect(response.status == Status.Created)
  }

  test("GET /users/profile should return user profile for authenticated user") {
    val routes = UserRoutes[IO](mockUsersService)
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = tm.domain.CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Employee,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.GET,
        uri = uri"/profile",
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.NotFound) // Mock returns None
  }

  test("GET /users/company should return users in company") {
    val routes = UserRoutes[IO](mockUsersService)
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = tm.domain.CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Employee,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.GET,
        uri = uri"/company",
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Ok)
  }

  test("DELETE /users/{userId} should delete a user") {
    val routes = UserRoutes[IO](mockUsersService)
    val userId = UUID.randomUUID()
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = tm.domain.CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Director,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.DELETE,
        uri = Uri.unsafeFromString(s"/$userId"),
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.NoContent)
  }
}
