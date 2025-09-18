package tm.generators

import java.util.UUID

import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus
import tm.domain.task._

object KanbanGenerators {
  def taskPriorityGen: TaskPriority = TaskPriority.Medium

  def taskStatusGen: TaskStatus = TaskStatus.ToDo

  def kanbanTaskGen: KanbanTask = KanbanTask(
    id = TaskId(UUID.randomUUID()),
    name = "Sample Task",
    description = Some("Sample description"),
    assignees = List(PersonId(UUID.randomUUID())),
    priority = Some(TaskPriority.Medium),
    estimatedHours = Some(4),
    tags = List("test"),
    position = 0,
    dueDate = None,
    status = TaskStatus.ToDo,
  )

  def kanbanColumnGen: KanbanColumn = KanbanColumn(
    status = TaskStatus.ToDo,
    name = "To Do",
    tasks = List(kanbanTaskGen),
    wipLimit = None,
    position = 0,
  )

  def kanbanBoardGen: KanbanBoard = KanbanBoard(
    projectId = ProjectId(UUID.randomUUID()),
    columns = List(kanbanColumnGen),
  )

  def taskMoveGen: TaskMove = TaskMove(
    taskId = TaskId(UUID.randomUUID()),
    newStatus = TaskStatus.InProgress,
    newPosition = 1,
  )

  def bulkTaskMoveRequestGen: BulkTaskMoveRequest = BulkTaskMoveRequest(
    moves = List(taskMoveGen)
  )
}
