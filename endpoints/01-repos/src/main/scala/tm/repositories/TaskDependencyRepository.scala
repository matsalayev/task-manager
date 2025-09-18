package tm.repositories

import cats.effect.Resource
import cats.effect.kernel.MonadCancelThrow
import skunk.Session

import tm.domain.PersonId
import tm.domain.TaskId
import tm.domain.task.TaskDependency
import tm.domain.task.TaskSubtask

trait TaskDependencyRepository[F[_]] {
  def addDependency(dependency: TaskDependency): F[TaskDependency]
  def removeDependency(dependentTaskId: TaskId, dependencyTaskId: TaskId): F[Unit]
  def findDependenciesByTask(taskId: TaskId): F[List[TaskDependency]]
  def findDependentsByTask(taskId: TaskId): F[List[TaskDependency]]

  def addSubtask(subtask: TaskSubtask): F[TaskSubtask]
  def removeSubtask(parentTaskId: TaskId, childTaskId: TaskId): F[Unit]
  def findSubtasksByParent(parentTaskId: TaskId): F[List[TaskSubtask]]
  def findParentByChild(childTaskId: TaskId): F[Option[TaskSubtask]]
  def reorderSubtasks(parentTaskId: TaskId, subtaskOrders: List[(TaskId, Int)]): F[Unit]
}

object TaskDependencyRepository {
  def make[F[_]: MonadCancelThrow](
      implicit
      session: Resource[F, Session[F]]
    ): TaskDependencyRepository[F] =
    new TaskDependencyRepository[F] {
      def addDependency(dependency: TaskDependency): F[TaskDependency] = ???
      def removeDependency(dependentTaskId: TaskId, dependencyTaskId: TaskId): F[Unit] = ???
      def findDependenciesByTask(taskId: TaskId): F[List[TaskDependency]] = ???
      def findDependentsByTask(taskId: TaskId): F[List[TaskDependency]] = ???

      def addSubtask(subtask: TaskSubtask): F[TaskSubtask] = ???
      def removeSubtask(parentTaskId: TaskId, childTaskId: TaskId): F[Unit] = ???
      def findSubtasksByParent(parentTaskId: TaskId): F[List[TaskSubtask]] = ???
      def findParentByChild(childTaskId: TaskId): F[Option[TaskSubtask]] = ???
      def reorderSubtasks(parentTaskId: TaskId, subtaskOrders: List[(TaskId, Int)]): F[Unit] = ???
    }
}
