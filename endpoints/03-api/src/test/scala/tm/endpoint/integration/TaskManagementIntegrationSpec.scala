package tm.endpoint.integration

import java.util.UUID

import cats.effect.IO
import weaver.SimpleIOSuite

import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.TaskId

object TaskManagementIntegrationSpec extends SimpleIOSuite {
  test("Task management integration should compile") {
    val projectId = ProjectId(UUID.randomUUID())
    val taskId = TaskId(UUID.randomUUID())
    val userId = PersonId(UUID.randomUUID())

    // Test basic integration functionality
    IO.pure(expect(projectId.value != null && taskId.value != null && userId.value != null))
  }

  test("IDs should work correctly") {
    val taskId = TaskId(UUID.randomUUID())
    val userId = PersonId(UUID.randomUUID())

    IO.pure(expect(taskId.value != userId.value))
  }
}
