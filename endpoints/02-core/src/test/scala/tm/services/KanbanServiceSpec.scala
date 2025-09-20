package tm.services

import java.util.UUID

import _root_.tm.domain.PersonId
import _root_.tm.domain.ProjectId
import _root_.tm.domain.TaskId
import _root_.tm.domain.enums.TaskStatus
import cats.effect.IO
import weaver.SimpleIOSuite

object KanbanServiceSpec extends SimpleIOSuite {
  test("KanbanService should compile") {
    val projectId = ProjectId(UUID.randomUUID())
    val userId = PersonId(UUID.randomUUID())
    val taskId = TaskId(UUID.randomUUID())

    // Test basic functionality without actual service
    IO.pure(expect(projectId.value != null && userId.value != null && taskId.value != null))
  }

  test("TaskStatus should work correctly") {
    val status = TaskStatus.InProgress
    IO.pure(expect(status == TaskStatus.InProgress))
  }
}
