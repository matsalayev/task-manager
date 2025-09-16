package tm.endpoint.routes

import java.util.UUID

import cats.effect.IO
import eu.timepit.refined.types.string.NonEmptyString
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import weaver.SimpleIOSuite

import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.TagId
import tm.domain.TaskId
import tm.domain.auth.AuthedUser
import tm.domain.enums.TaskStatus
import tm.domain.task.CreateTag
import tm.domain.task.Tag
import tm.domain.task.Task
import tm.domain.task.TaskAssignment
import tm.domain.task.TaskCreation
import tm.domain.task.TaskUpdate
import tm.services.TasksService
import tm.syntax.refined._

object TasksRoutesSpec extends SimpleIOSuite {

  // Mock service for testing
  def mockTasksService: TasksService[IO] = new TasksService[IO] {
    override def createTask(creation: TaskCreation, createdBy: PersonId): IO[TaskId] =
      IO.pure(TaskId(UUID.randomUUID()))

    override def getTasksByProject(projectId: ProjectId): IO[List[Task]] =
      IO.pure(List.empty)

    override def getTaskById(taskId: TaskId): IO[Option[Task]] =
      IO.pure(None)

    override def updateTask(taskId: TaskId, update: TaskUpdate): IO[Unit] =
      IO.unit

    override def deleteTask(taskId: TaskId): IO[Unit] =
      IO.unit

    override def assignTask(assignment: TaskAssignment): IO[Unit] =
      IO.unit

    override def createTag(tag: CreateTag, corporateId: CorporateId): IO[Unit] =
      IO.unit

    override def getTagById(tagId: TagId): IO[Option[Tag]] =
      IO.pure(None)

    override def deleteTag(tagId: TagId): IO[Unit] =
      IO.unit
  }

  def sampleTaskCreation: TaskCreation = TaskCreation(
    projectId = ProjectId(UUID.randomUUID()),
    name = NonEmptyString.unsafeFrom("Test Task"),
    description = Some(NonEmptyString.unsafeFrom("Test description")),
    tagId = Some(TagId(UUID.randomUUID())),
    photo = None,
    status = TaskStatus.ToDo,
    deadline = None,
    assignees = List.empty,
    link = None,
  )

  def sampleTaskUpdate: TaskUpdate = TaskUpdate(
    name = Some(NonEmptyString.unsafeFrom("Updated Task")),
    description = Some(NonEmptyString.unsafeFrom("Updated description")),
    status = Some(TaskStatus.InProgress),
  )

  def sampleTaskAssignment: TaskAssignment = TaskAssignment(
    taskId = TaskId(UUID.randomUUID()),
    assignees = List(PersonId(UUID.randomUUID())),
  )

  def sampleCreateTag: CreateTag = CreateTag(
    name = NonEmptyString.unsafeFrom("Bug"),
    color = NonEmptyString.unsafeFrom("#FF0000"),
  )

  implicit val logger: org.typelevel.log4cats.Logger[IO] =
    org.typelevel.log4cats.noop.NoOpLogger.impl[IO]

  test("POST /tasks should create a new task") {
    val routes = TasksRoutes[IO](mockTasksService)
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Employee,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.POST,
        uri = uri"/",
      ).withEntity(sampleTaskCreation),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Created)
  }

  test("GET /tasks/project/{projectId} should return tasks by project") {
    val routes = TasksRoutes[IO](mockTasksService)
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
        uri = Uri.unsafeFromString(s"/project/$projectId"),
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Ok)
  }

  test("GET /tasks/{taskId} should return task by ID") {
    val routes = TasksRoutes[IO](mockTasksService)
    val taskId = UUID.randomUUID()
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
        uri = Uri.unsafeFromString(s"/$taskId"),
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.NotFound) // Mock returns None
  }

  test("PUT /tasks/{taskId} should update a task") {
    val routes = TasksRoutes[IO](mockTasksService)
    val taskId = UUID.randomUUID()
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
        uri = Uri.unsafeFromString(s"/$taskId"),
      ).withEntity(sampleTaskUpdate),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.NoContent)
  }

  test("DELETE /tasks/{taskId} should delete a task") {
    val routes = TasksRoutes[IO](mockTasksService)
    val taskId = UUID.randomUUID()
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Manager,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.DELETE,
        uri = Uri.unsafeFromString(s"/$taskId"),
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.NoContent)
  }

  test("POST /tasks/{taskId}/assign should assign task to users") {
    val routes = TasksRoutes[IO](mockTasksService)
    val taskId = UUID.randomUUID()
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Manager,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString(s"/$taskId/assign"),
      ).withEntity(sampleTaskAssignment.copy(taskId = TaskId(taskId))),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.NoContent)
  }

  test("POST /tasks/tags should create a new tag") {
    val routes = TasksRoutes[IO](mockTasksService)
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Manager,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.POST,
        uri = uri"/tags",
      ).withEntity(sampleCreateTag),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Created)
  }

  test("GET /tasks/tags/{tagId} should return tag by ID") {
    val routes = TasksRoutes[IO](mockTasksService)
    val tagId = UUID.randomUUID()
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
        uri = Uri.unsafeFromString(s"/tags/$tagId"),
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.NotFound) // Mock returns None
  }

  test("DELETE /tasks/tags/{tagId} should delete a tag") {
    val routes = TasksRoutes[IO](mockTasksService)
    val tagId = UUID.randomUUID()
    val user = AuthedUser.User(
      id = PersonId(UUID.randomUUID()),
      corporateId = CorporateId(UUID.randomUUID()),
      role = tm.domain.enums.Role.Manager,
      phone = "+998901234567",
    )

    val authedRequest = AuthedRequest(
      user: AuthedUser,
      Request[IO](
        method = Method.DELETE,
        uri = Uri.unsafeFromString(s"/tags/$tagId"),
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.NoContent)
  }
}
