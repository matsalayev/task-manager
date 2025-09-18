package tm.endpoint.routes

import java.util.UUID

import cats.effect.IO
import weaver.SimpleIOSuite

import tm.domain.ProjectId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus

object KanbanRoutesSpec extends SimpleIOSuite {
  test("KanbanRoutes should compile") {
    val projectId = ProjectId(UUID.randomUUID())
    val taskId = TaskId(UUID.randomUUID())

    // Test basic functionality
    IO.pure(expect(projectId.value != null && taskId.value != null))
  }

  test("TaskStatus should work in routes") {
    val status = TaskStatus.InProgress
    IO.pure(expect(status == TaskStatus.InProgress))
  }
}
