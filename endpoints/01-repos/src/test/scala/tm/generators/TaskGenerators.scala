package tm.generators

import java.time.ZonedDateTime
import java.util.UUID

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus
import tm.domain.task.Task
import tm.domain.task.TaskCreation

object TaskGenerators {
  def taskGen: Task = Task(
    id = TaskId(UUID.randomUUID()),
    createdAt = ZonedDateTime.now(),
    createdBy = PersonId(UUID.randomUUID()),
    projectId = ProjectId(UUID.randomUUID()),
    name = NonEmptyString.unsafeFrom("Sample Task"),
    description = Some(NonEmptyString.unsafeFrom("Sample task description")),
    tagId = None,
    photo = None,
    status = TaskStatus.ToDo,
    deadline = None,
    link = None,
  )

  def taskIdGen: TaskId = TaskId(UUID.randomUUID())

  def taskCreateGen: TaskCreation = TaskCreation(
    projectId = ProjectId(UUID.randomUUID()),
    name = NonEmptyString.unsafeFrom("New Task"),
    description = Some(NonEmptyString.unsafeFrom("New task description")),
    tagId = None,
    photo = None,
    status = TaskStatus.ToDo,
    deadline = None,
    assignees = List.empty,
    link = None,
  )
}
