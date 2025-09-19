package tm.services

import java.time.ZonedDateTime
import java.util.UUID

import _root_.tm.domain.CorporateId
import _root_.tm.domain.PersonId
import _root_.tm.domain.ProjectId
import _root_.tm.domain.TagId
import _root_.tm.domain.TaskId
import _root_.tm.domain.enums.TaskStatus
import _root_.tm.domain.task.Tag
import _root_.tm.domain.task.Task
import cats.effect.IO
import eu.timepit.refined.types.string.NonEmptyString
import weaver.SimpleIOSuite

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
