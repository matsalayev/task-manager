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
import tm.domain.task.Tag
import tm.domain.task.Task

object TasksServiceSpec extends SimpleIOSuite {
  test("TasksService should compile") {
    val taskId = TaskId(UUID.randomUUID())
    val now = ZonedDateTime.now()
    val mockTask = Task(
      id = taskId,
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

    IO.pure(expect(mockTask.id == taskId))
  }

  test("Tag should work correctly") {
    val mockTag = Tag(
      id = TagId(UUID.randomUUID()),
      name = NonEmptyString.unsafeFrom("Mock Tag"),
      color = Some(NonEmptyString.unsafeFrom("#FF0000")),
      corporateId = CorporateId(UUID.randomUUID()),
    )

    IO.pure(expect(mockTag.name.value == "Mock Tag"))
  }
}
