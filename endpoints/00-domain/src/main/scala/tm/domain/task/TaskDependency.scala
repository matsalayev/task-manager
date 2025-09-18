package tm.domain.task

import java.time.ZonedDateTime

import io.circe.Codec
import io.circe.generic.semiauto._
import monocle.Iso

import tm.domain.PersonId
import tm.domain.TaskId
import tm.effects.IsUUID
import tm.syntax.circe._

case class TaskDependencyId(value: java.util.UUID) extends AnyVal

case class TaskDependency(
    id: TaskDependencyId,
    dependentTaskId: TaskId,
    dependencyTaskId: TaskId,
    dependencyType: DependencyType,
    createdAt: ZonedDateTime,
  )

case class TaskDependencyCreate(
    dependentTaskId: TaskId,
    dependencyTaskId: TaskId,
    dependencyType: DependencyType,
  )

sealed trait DependencyType
object DependencyType {
  case object FinishToStart extends DependencyType // Task B cannot start until Task A finishes (default)
  case object StartToStart extends DependencyType // Task B cannot start until Task A starts
  case object FinishToFinish extends DependencyType // Task B cannot finish until Task A finishes
  case object StartToFinish extends DependencyType // Task B cannot finish until Task A starts

  implicit val codec: Codec[DependencyType] = deriveCodec
}

case class DependencyValidationResult(
    isValid: Boolean,
    errorMessage: Option[String],
  )

// === SUBTASKS ===

case class TaskSubtaskId(value: java.util.UUID) extends AnyVal

case class TaskSubtask(
    id: TaskSubtaskId,
    parentTaskId: TaskId,
    childTaskId: TaskId,
    orderIndex: Int,
    createdBy: PersonId,
    createdAt: ZonedDateTime,
  )

case class TaskSubtaskCreate(
    parentTaskId: TaskId,
    childTaskId: TaskId,
    orderIndex: Int,
  )

// === ENHANCED MODELS ===

case class TaskHierarchy(
    task: Task,
    subtasks: List[TaskHierarchy],
    dependencies: List[TaskDependency],
    dependents: List[TaskDependency],
  )

case class DependencyAnalysis(
    hasCyclicDependencies: Boolean,
    criticalPath: List[TaskId],
    blockedTasks: List[TaskId],
    readyTasks: List[TaskId],
  )

// === REQUEST/RESPONSE DTOs ===

case class CreateDependencyRequest(
    dependsOnTaskId: TaskId,
    dependencyType: DependencyType,
  )

case class CreateSubtaskRequest(
    childTaskId: TaskId,
    orderIndex: Option[Int],
  )

case class ReorderSubtasksRequest(
    subtaskOrders: List[SubtaskOrder]
  )

case class SubtaskOrder(
    subtaskId: TaskSubtaskId,
    orderIndex: Int,
  )

case class TaskWithDependencies(
    task: Task,
    dependencies: List[TaskDependencyInfo],
    dependents: List[TaskDependencyInfo],
    subtasks: List[TaskWithDependencies],
    parentTask: Option[TaskId],
    isBlocked: Boolean,
    canStart: Boolean,
  )

case class TaskDependencyInfo(
    dependency: TaskDependency,
    dependentTask: Task,
    isCompleted: Boolean,
  )

// === CODECS ===

object TaskDependencyId {
  implicit val codec: Codec[TaskDependencyId] = deriveCodec
  implicit val isUUID: IsUUID[TaskDependencyId] = new IsUUID[TaskDependencyId] {
    val uuid: Iso[java.util.UUID, TaskDependencyId] = Iso(TaskDependencyId(_))(_.value)
  }
}

object TaskDependency {
  implicit val codec: Codec[TaskDependency] = deriveCodec
}

object TaskDependencyCreate {
  implicit val codec: Codec[TaskDependencyCreate] = deriveCodec
}

object DependencyValidationResult {
  implicit val codec: Codec[DependencyValidationResult] = deriveCodec
}

object TaskSubtaskId {
  implicit val codec: Codec[TaskSubtaskId] = deriveCodec
  implicit val isUUID: IsUUID[TaskSubtaskId] = new IsUUID[TaskSubtaskId] {
    val uuid: Iso[java.util.UUID, TaskSubtaskId] = Iso(TaskSubtaskId(_))(_.value)
  }
}

object TaskSubtask {
  implicit val codec: Codec[TaskSubtask] = deriveCodec
}

object TaskSubtaskCreate {
  implicit val codec: Codec[TaskSubtaskCreate] = deriveCodec
}

object TaskHierarchy {
  implicit val codec: Codec[TaskHierarchy] = deriveCodec
}

object DependencyAnalysis {
  implicit val codec: Codec[DependencyAnalysis] = deriveCodec
}

object CreateDependencyRequest {
  implicit val codec: Codec[CreateDependencyRequest] = deriveCodec
}

object CreateSubtaskRequest {
  implicit val codec: Codec[CreateSubtaskRequest] = deriveCodec
}

object ReorderSubtasksRequest {
  implicit val codec: Codec[ReorderSubtasksRequest] = deriveCodec
}

object SubtaskOrder {
  implicit val codec: Codec[SubtaskOrder] = deriveCodec
}

object TaskWithDependencies {
  implicit val codec: Codec[TaskWithDependencies] = deriveCodec
}

object TaskDependencyInfo {
  implicit val codec: Codec[TaskDependencyInfo] = deriveCodec
}
