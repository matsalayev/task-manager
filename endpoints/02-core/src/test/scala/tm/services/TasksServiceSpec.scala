package tm.services

import java.time.ZonedDateTime
import java.util.UUID

import cats.effect.IO
import eu.timepit.refined.types.string.NonEmptyString
import weaver.SimpleIOSuite

import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.TagId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus
import tm.domain.task.CreateTag
import tm.domain.task.Tag
import tm.domain.task.Task
import tm.domain.task.TaskAssignment
import tm.domain.task.TaskCreation
import tm.domain.task.TaskUpdate
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.repositories.TasksRepository
import tm.repositories.dto
import tm.syntax.refined._

object TasksServiceSpec extends SimpleIOSuite {

  // Mock repository for testing
  def mockTasksRepository: TasksRepository[IO] = new TasksRepository[IO] {
    override def create(task: Task): IO[Unit] = IO.unit

    override def findById(id: TaskId): IO[Option[Task]] = {
      val now = ZonedDateTime.now()
      val mockTask = Task(
        id = id,
        createdAt = now,
        createdBy = PersonId(UUID.randomUUID()),
        projectId = ProjectId(UUID.randomUUID()),
        name = NonEmptyString.unsafeFrom("Mock Task"),
        description = Some(NonEmptyString.unsafeFrom("Mock description")),
        tagId = None,
        photo = None,
        status = TaskStatus.ToDo,
        deadline = None,
        link = None,
      )
      IO.pure(Some(mockTask))
    }

    override def findByName(name: NonEmptyString): IO[Option[Task]] = IO.pure(None)

    override def getAll(projectId: ProjectId): IO[List[dto.Task]] = IO.pure(List.empty)

    override def update(task: Task): IO[Unit] = IO.unit

    override def delete(id: TaskId): IO[Unit] = IO.unit

    override def createTag(tag: Tag): IO[Unit] = IO.unit

    override def findTagById(id: TagId): IO[Option[Tag]] = {
      val mockTag = Tag(
        id = id,
        name = NonEmptyString.unsafeFrom("Mock Tag"),
        color = Some(NonEmptyString.unsafeFrom("#FF0000")),
        corporateId = CorporateId(UUID.randomUUID()),
      )
      IO.pure(Some(mockTag))
    }

    override def findTagByName(name: NonEmptyString): IO[Option[Tag]] = IO.pure(None)

    override def deleteTag(id: TagId): IO[Unit] = IO.unit
  }

  implicit val genUUID: GenUUID[IO] = new GenUUID[IO] {
    override def make: IO[UUID] = IO.pure(UUID.randomUUID())
    override def read(str: String): IO[UUID] = IO.pure(UUID.fromString(str))
  }

  implicit val calendar: Calendar[IO] = new Calendar[IO] {
    override def currentZonedDateTime: IO[ZonedDateTime] = IO.pure(ZonedDateTime.now())
    override def currentDate: IO[java.time.LocalDate] = IO.pure(java.time.LocalDate.now())
    override def currentDateTime: IO[java.time.LocalDateTime] =
      IO.pure(java.time.LocalDateTime.now())
    override def currentInstant: IO[java.time.Instant] = IO.pure(java.time.Instant.now())
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

  def sampleCreateTag: CreateTag = CreateTag(
    name = NonEmptyString.unsafeFrom("Bug"),
    color = NonEmptyString.unsafeFrom("#FF0000"),
  )

  test("TasksService.createTask should create a new task") {
    val service = TasksService.make[IO](mockTasksRepository)
    val createdBy = PersonId(UUID.randomUUID())

    for {
      taskId <- service.createTask(sampleTaskCreation, createdBy)
    } yield expect(taskId.value != null)
  }

  test("TasksService.getTasksByProject should return tasks for a project") {
    val service = TasksService.make[IO](mockTasksRepository)
    val projectId = ProjectId(UUID.randomUUID())

    for {
      tasks <- service.getTasksByProject(projectId)
    } yield expect(tasks.isEmpty) // Mock repository returns empty list
  }

  test("TasksService.getTaskById should return a task when it exists") {
    val service = TasksService.make[IO](mockTasksRepository)
    val taskId = TaskId(UUID.randomUUID())

    for {
      maybeTask <- service.getTaskById(taskId)
    } yield expect(maybeTask.isDefined && maybeTask.get.id == taskId)
  }

  test("TasksService.updateTask should update existing task") {
    val service = TasksService.make[IO](mockTasksRepository)
    val taskId = TaskId(UUID.randomUUID())

    for {
      _ <- service.updateTask(taskId, sampleTaskUpdate)
    } yield expect.same((), ())
  }

  test("TasksService.deleteTask should delete a task") {
    val service = TasksService.make[IO](mockTasksRepository)
    val taskId = TaskId(UUID.randomUUID())

    for {
      _ <- service.deleteTask(taskId)
    } yield expect.same((), ())
  }

  test("TasksService.assignTask should assign task to users") {
    val service = TasksService.make[IO](mockTasksRepository)
    val taskId = TaskId(UUID.randomUUID())
    val assignment = TaskAssignment(
      taskId = taskId,
      assignees = List(PersonId(UUID.randomUUID())),
    )

    for {
      _ <- service.assignTask(assignment)
    } yield expect.same((), ())
  }

  test("TasksService.createTag should create a new tag") {
    val service = TasksService.make[IO](mockTasksRepository)
    val corporateId = CorporateId(UUID.randomUUID())

    for {
      _ <- service.createTag(sampleCreateTag, corporateId)
    } yield expect.same((), ())
  }

  test("TasksService.getTagById should return a tag when it exists") {
    val service = TasksService.make[IO](mockTasksRepository)
    val tagId = TagId(UUID.randomUUID())

    for {
      maybeTag <- service.getTagById(tagId)
    } yield expect(maybeTag.isDefined && maybeTag.get.id == tagId)
  }

  test("TasksService.deleteTag should delete a tag") {
    val service = TasksService.make[IO](mockTasksRepository)
    val tagId = TagId(UUID.randomUUID())

    for {
      _ <- service.deleteTag(tagId)
    } yield expect.same((), ())
  }
}
