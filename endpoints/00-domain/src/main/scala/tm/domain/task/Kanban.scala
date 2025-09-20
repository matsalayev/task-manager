package tm.domain.task

import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.refined._

import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.enums.TaskStatus
import tm.syntax.circe._

case class KanbanBoard(
    projectId: ProjectId,
    columns: List[KanbanColumn],
  )

case class KanbanColumn(
    status: TaskStatus,
    name: String,
    tasks: List[KanbanTask],
    wipLimit: Option[Int] = None,
    position: Int,
  )

case class KanbanTask(
    id: tm.domain.TaskId,
    name: String,
    description: Option[String],
    assignees: List[PersonId],
    priority: Option[TaskPriority],
    estimatedHours: Option[Int],
    tags: List[String],
    position: Int,
    dueDate: Option[java.time.ZonedDateTime],
    status: TaskStatus,
  )

sealed trait TaskPriority
object TaskPriority {
  case object Low extends TaskPriority
  case object Medium extends TaskPriority
  case object High extends TaskPriority
  case object Critical extends TaskPriority

  implicit val codec: Codec[TaskPriority] = deriveCodec
}

// Request DTOs
case class TaskMoveRequest(
    newStatus: TaskStatus,
    newPosition: Int,
  )

case class TaskMove(
    taskId: tm.domain.TaskId,
    newStatus: TaskStatus,
    newPosition: Int,
  )

case class BulkTaskMoveRequest(
    moves: List[TaskMove]
  )

case class UpdateWipLimitRequest(
    status: TaskStatus,
    wipLimit: Option[Int],
  )

// Codecs
object KanbanBoard {
  implicit val codec: Codec[KanbanBoard] = deriveCodec
}

object KanbanColumn {
  implicit val codec: Codec[KanbanColumn] = deriveCodec
}

object KanbanTask {
  implicit val codec: Codec[KanbanTask] = deriveCodec
}

object TaskMoveRequest {
  implicit val codec: Codec[TaskMoveRequest] = deriveCodec
}

object TaskMove {
  implicit val codec: Codec[TaskMove] = deriveCodec
}

object BulkTaskMoveRequest {
  implicit val codec: Codec[BulkTaskMoveRequest] = deriveCodec
}

object UpdateWipLimitRequest {
  implicit val codec: Codec[UpdateWipLimitRequest] = deriveCodec
}
