package tm.endpoint.routes

import java.util.UUID

import cats.effect.IO
import eu.timepit.refined.types.string.NonEmptyString
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import weaver.SimpleIOSuite

import tm.domain.CorporateId
import tm.domain.PaginatedResponse
import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.auth.AuthedUser
import tm.domain.project.Project
import tm.domain.project.ProjectCreation
import tm.domain.project.ProjectUpdate
import tm.services.ProjectsService
import tm.syntax.refined._

object ProjectsRoutesSpec extends SimpleIOSuite {

  // Mock service for testing
  def mockProjectsService: ProjectsService[IO] = new ProjectsService[IO] {
    override def createProject(creation: ProjectCreation, createdBy: PersonId): IO[ProjectId] =
      IO.pure(ProjectId(UUID.randomUUID()))

    override def getProjects(
        corporateId: CorporateId,
        limit: Int,
        page: Int,
      ): IO[PaginatedResponse[Project]] =
      IO.pure(PaginatedResponse(List.empty, 0L))

    override def getProjectById(projectId: ProjectId): IO[Option[Project]] =
      IO.pure(None)

    override def updateProject(projectId: ProjectId, update: ProjectUpdate): IO[Unit] =
      IO.unit

    override def deleteProject(projectId: ProjectId): IO[Unit] =
      IO.unit
  }

  def sampleProjectCreation: ProjectCreation = {
    import tm.syntax.refined._
    ProjectCreation(
      name = NonEmptyString.unsafeFrom("Test Project"),
      description = Some(NonEmptyString.unsafeFrom("Test description")),
      corporateId = CorporateId(UUID.randomUUID()),
    )
  }

  def sampleProjectUpdate: ProjectUpdate = ProjectUpdate(
    name = Some(NonEmptyString.unsafeFrom("Updated Name")),
    description = None,
  )

  implicit val logger: org.typelevel.log4cats.Logger[IO] =
    org.typelevel.log4cats.noop.NoOpLogger.impl[IO]

  test("POST /projects should create a new project") {
    val routes = ProjectsRoutes[IO](mockProjectsService)
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Director,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.POST,
        uri = uri"/",
      ).withEntity(sampleProjectCreation),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Created)
  }

  test("GET /projects should return projects list") {
    val routes = ProjectsRoutes[IO](mockProjectsService)
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Manager,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.GET,
        uri = uri"/",
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Ok)
  }

  test("GET /projects with pagination should return projects list") {
    val routes = ProjectsRoutes[IO](mockProjectsService)
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Employee,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.GET,
        uri = uri"/?limit=10&page=1",
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Ok)
  }

  test("GET /projects/{projectId} should return project by ID") {
    val routes = ProjectsRoutes[IO](mockProjectsService)
    val projectId = UUID.randomUUID()
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Employee,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"/$projectId"),
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.NotFound) // Mock returns None
  }

  test("PUT /projects/{projectId} should update a project") {
    val routes = ProjectsRoutes[IO](mockProjectsService)
    val projectId = UUID.randomUUID()
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Manager,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.PUT,
        uri = Uri.unsafeFromString(s"/$projectId"),
      ).withEntity(sampleProjectUpdate),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.NoContent)
  }

  test("DELETE /projects/{projectId} should delete a project") {
    val routes = ProjectsRoutes[IO](mockProjectsService)
    val projectId = UUID.randomUUID()
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Director,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.DELETE,
        uri = Uri.unsafeFromString(s"/$projectId"),
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.NoContent)
  }
}
