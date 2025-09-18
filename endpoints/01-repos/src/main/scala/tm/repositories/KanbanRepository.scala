package tm.repositories

import cats.effect.Resource
import cats.effect.kernel.MonadCancelThrow
import skunk.Session

import tm.domain.ProjectId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus
import tm.domain.task.KanbanBoard
import tm.domain.task.Task
import tm.domain.task.TaskMove

trait KanbanRepository[F[_]] {
  def getKanbanBoard(projectId: ProjectId): F[KanbanBoard]
  def moveTask(
      taskId: TaskId,
      newStatus: TaskStatus,
      newPosition: Int,
    ): F[Option[Task]]
  def bulkMoveTask(moves: List[TaskMove]): F[Unit]
  def reorderTasksInColumn(
      projectId: ProjectId,
      status: TaskStatus,
      taskIds: List[TaskId],
    ): F[Unit]
  def getTasksByStatus(projectId: ProjectId, status: TaskStatus): F[List[Task]]
}

object KanbanRepository {
  def make[F[_]: MonadCancelThrow](implicit session: Resource[F, Session[F]]): KanbanRepository[F] =
    new KanbanRepository[F] {
      def getKanbanBoard(projectId: ProjectId): F[KanbanBoard] = ???
      def moveTask(
          taskId: TaskId,
          newStatus: TaskStatus,
          newPosition: Int,
        ): F[Option[Task]] = ???
      def bulkMoveTask(moves: List[TaskMove]): F[Unit] = ???
      def reorderTasksInColumn(
          projectId: ProjectId,
          status: TaskStatus,
          taskIds: List[TaskId],
        ): F[Unit] = ???
      def getTasksByStatus(projectId: ProjectId, status: TaskStatus): F[List[Task]] = ???
    }
}
