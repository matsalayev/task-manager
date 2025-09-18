package tm.services

import cats.effect.kernel.MonadCancelThrow
import cats.implicits._

import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.TaskId
import tm.domain.enums.TaskStatus
import tm.domain.task.KanbanBoard
import tm.domain.task.Task
import tm.domain.task.TaskMove
import tm.repositories.KanbanRepository
import tm.repositories.TasksRepository

trait KanbanService[F[_]] {
  def getKanbanBoard(projectId: ProjectId, userId: PersonId): F[KanbanBoard]
  def moveTask(
      taskId: TaskId,
      newStatus: TaskStatus,
      newPosition: Int,
      userId: PersonId,
    ): F[Task]
  def bulkMoveTask(moves: List[TaskMove], userId: PersonId): F[Unit]
}

object KanbanService {
  def make[F[_]: MonadCancelThrow](
      kanbanRepository: KanbanRepository[F],
      tasksRepository: TasksRepository[F],
    ): KanbanService[F] =
    new KanbanService[F] {
      def getKanbanBoard(projectId: ProjectId, userId: PersonId): F[KanbanBoard] =
        kanbanRepository.getKanbanBoard(projectId)

      def moveTask(
          taskId: TaskId,
          newStatus: TaskStatus,
          newPosition: Int,
          userId: PersonId,
        ): F[Task] =
        for {
          movedTask <- kanbanRepository.moveTask(taskId, newStatus, newPosition)
          result <- movedTask.liftTo[F](new RuntimeException("Failed to move task"))
        } yield result

      def bulkMoveTask(moves: List[TaskMove], userId: PersonId): F[Unit] =
        kanbanRepository.bulkMoveTask(moves)
    }
}
