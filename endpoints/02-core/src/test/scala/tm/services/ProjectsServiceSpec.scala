package tm.services

import java.util.UUID

import _root_.tm.domain.CorporateId
import _root_.tm.domain.PaginatedResponse
import _root_.tm.domain.PersonId
import _root_.tm.domain.ProjectId
import _root_.tm.domain.project.Project
import _root_.tm.domain.project.ProjectCreation
import _root_.tm.domain.project.ProjectUpdate
import _root_.tm.repositories.ProjectsRepository
import _root_.tm.syntax.refined._
import cats.effect.IO
import eu.timepit.refined.types.string.NonEmptyString
import weaver.SimpleIOSuite

object ProjectsServiceSpec extends SimpleIOSuite {

  // Mock repository for unit testing
  def mockProjectsRepo: ProjectsRepository[IO] = new ProjectsRepository[IO] {
    override def create(project: Project): IO[Unit] = IO.unit

    override def getAll(
        corporateId: CorporateId,
        limit: Int,
        page: Int,
      ): IO[PaginatedResponse[Project]] =
      IO.pure(PaginatedResponse(List.empty, 0L))

    override def findById(projectId: ProjectId): IO[Option[Project]] = IO.pure(None)

    override def findByName(name: NonEmptyString): IO[Option[Project]] = IO.pure(None)

    override def update(project: Project): IO[Unit] = IO.unit

    override def delete(projectId: ProjectId): IO[Unit] = IO.unit
  }

  def sampleProjectCreation: ProjectCreation = {
    import tm.syntax.refined._
    ProjectCreation(
      name = NonEmptyString.unsafeFrom("Test Project"),
      description = Some(NonEmptyString.unsafeFrom("Test project description")),
      corporateId = CorporateId(UUID.randomUUID()),
    )
  }

  def sampleProjectUpdate: ProjectUpdate = ProjectUpdate(
    name = Some(NonEmptyString.unsafeFrom("Updated Project Name")),
    description = Some(NonEmptyString.unsafeFrom("Updated description")),
  )

  test("createProject should create a new project") {
    val projectsService = ProjectsService.make[IO](mockProjectsRepo)
    val createdBy = PersonId(UUID.randomUUID())

    for {
      result <- projectsService.createProject(sampleProjectCreation, createdBy)
    } yield expect(result != null) // Just verify it returns a ProjectId
  }

  test("getProjects should return paginated projects") {
    val projectsService = ProjectsService.make[IO](mockProjectsRepo)
    val corporateId = CorporateId(UUID.randomUUID())

    for {
      result <- projectsService.getProjects(corporateId, 10, 1)
    } yield expect(result.data.isEmpty) and expect(result.total == 0L)
  }

  test("getProjectById should return None for non-existent project") {
    val projectsService = ProjectsService.make[IO](mockProjectsRepo)
    val projectId = ProjectId(UUID.randomUUID())

    for {
      project <- projectsService.getProjectById(projectId)
    } yield expect(project.isEmpty)
  }

  test("updateProject should fail for non-existent project") {
    val projectsService = ProjectsService.make[IO](mockProjectsRepo)
    val projectId = ProjectId(UUID.randomUUID())

    projectsService.updateProject(projectId, sampleProjectUpdate).attempt.map {
      case Left(error) => expect(error.getMessage.contains("not found"))
      case Right(_) => failure("Should have failed")
    }
  }

  test("deleteProject should complete successfully") {
    val projectsService = ProjectsService.make[IO](mockProjectsRepo)
    val projectId = ProjectId(UUID.randomUUID())

    for {
      _ <- projectsService.deleteProject(projectId)
    } yield success // Just verify it completes without error
  }
}
