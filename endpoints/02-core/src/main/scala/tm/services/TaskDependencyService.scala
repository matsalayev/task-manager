package tm.services

import cats.effect.kernel.MonadCancelThrow
import cats.implicits._

import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.TaskId
import tm.domain.task.TaskDependency
import tm.domain.task.TaskSubtask
import tm.repositories.TaskDependencyRepository
import tm.repositories.TasksRepository

trait TaskDependencyService[F[_]] {
  def addDependency(
      dependentTaskId: TaskId,
      dependencyTaskId: TaskId,
      userId: PersonId,
    ): F[TaskDependency]
  def removeDependency(
      dependentTaskId: TaskId,
      dependencyTaskId: TaskId,
      userId: PersonId,
    ): F[Unit]
  def addSubtask(
      parentTaskId: TaskId,
      childTaskId: TaskId,
      userId: PersonId,
    ): F[TaskSubtask]
  def removeSubtask(
      parentTaskId: TaskId,
      childTaskId: TaskId,
      userId: PersonId,
    ): F[Unit]
}

object TaskDependencyService {
  def make[F[_]: MonadCancelThrow](
      dependencyRepository: TaskDependencyRepository[F],
      tasksRepository: TasksRepository[F],
    ): TaskDependencyService[F] =
    new TaskDependencyService[F] {
      def addDependency(
          dependentTaskId: TaskId,
          dependencyTaskId: TaskId,
          userId: PersonId,
        ): F[TaskDependency] = {
        import tm.domain.task.{ DependencyType, TaskDependencyId }
        val dependency = TaskDependency(
          id = TaskDependencyId(java.util.UUID.randomUUID()),
          dependentTaskId = dependentTaskId,
          dependencyTaskId = dependencyTaskId,
          dependencyType = DependencyType.FinishToStart,
          createdAt = java.time.ZonedDateTime.now(),
        )
        dependencyRepository.addDependency(dependency)
      }

      def removeDependency(
          dependentTaskId: TaskId,
          dependencyTaskId: TaskId,
          userId: PersonId,
        ): F[Unit] =
        dependencyRepository.removeDependency(dependentTaskId, dependencyTaskId)

      def addSubtask(
          parentTaskId: TaskId,
          childTaskId: TaskId,
          userId: PersonId,
        ): F[TaskSubtask] = {
        import tm.domain.task.TaskSubtaskId
        val subtask = TaskSubtask(
          id = TaskSubtaskId(java.util.UUID.randomUUID()),
          parentTaskId = parentTaskId,
          childTaskId = childTaskId,
          orderIndex = 0,
          createdBy = userId,
          createdAt = java.time.ZonedDateTime.now(),
        )
        dependencyRepository.addSubtask(subtask)
      }

      def removeSubtask(
          parentTaskId: TaskId,
          childTaskId: TaskId,
          userId: PersonId,
        ): F[Unit] =
        dependencyRepository.removeSubtask(parentTaskId, childTaskId)
    }
}
