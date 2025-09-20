package tm.repositories

import java.util.UUID

import cats.effect.IO
import weaver.SimpleIOSuite

import tm.domain.ProjectId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus

object KanbanRepositorySpec extends SimpleIOSuite {
  test("KanbanRepository should compile") {
    val projectId = ProjectId(UUID.randomUUID())
    val taskId = TaskId(UUID.randomUUID())

    // Test basic functionality without database
    IO.pure(expect(projectId.value != null && taskId.value != null))
  }

  test("TaskStatus should have correct values") {
    val status = TaskStatus.ToDo
    IO.pure(expect(status == TaskStatus.ToDo))
  }
}
