package tm.repositories

import cats.effect.IO
import cats.implicits._

import tm.database.DBSuite
import tm.domain.enums.TaskStatus
import tm.domain.task._
import tm.generators.KanbanGenerators
import tm.generators.ProjectGenerators
import tm.generators.TaskGenerators
import tm.test.TestSuite

object KanbanRepositorySpec extends TestSuite with DBSuite {
  test("KanbanRepository should get kanban board for project") {
    withKanbanRepository { kanbanRepo =>
      for {
        project <- ProjectGenerators.projectGen.sample
        projectId = project.id

        // Create some tasks for the project
        task1 <- TaskGenerators
          .taskGen
          .map(_.copy(projectId = projectId, status = TaskStatus.Todo, position = 0))
          .sample
        task2 <- TaskGenerators
          .taskGen
          .map(_.copy(projectId = projectId, status = TaskStatus.InProgress, position = 1))
          .sample
        task3 <- TaskGenerators
          .taskGen
          .map(_.copy(projectId = projectId, status = TaskStatus.Done, position = 0))
          .sample

        // Get kanban board
        board <- kanbanRepo.getKanbanBoard(projectId)

        // Verify board structure
        _ <- expect(board.projectId == projectId).failFast
        _ <- expect(board.columns.length >= 3).failFast // At least Todo, InProgress, Done

        todoColumn = board.columns.find(_.status == TaskStatus.Todo)
        inProgressColumn = board.columns.find(_.status == TaskStatus.InProgress)
        doneColumn = board.columns.find(_.status == TaskStatus.Done)

        _ <- expect(todoColumn.isDefined).failFast
        _ <- expect(inProgressColumn.isDefined).failFast
        _ <- expect(doneColumn.isDefined).failFast

      } yield success
    }
  }

  test("KanbanRepository should move task to different status") {
    withKanbanRepository { kanbanRepo =>
      for {
        project <- ProjectGenerators.projectGen.sample
        task <- TaskGenerators
          .taskGen
          .map(
            _.copy(
              projectId = project.id,
              status = TaskStatus.Todo,
              position = 0,
            )
          )
          .sample

        // Move task to InProgress
        movedTaskOpt <- kanbanRepo.moveTask(task.id, TaskStatus.InProgress, 1)

        _ <- expect(movedTaskOpt.isDefined).failFast
        movedTask = movedTaskOpt.get
        _ <- expect(movedTask.status == TaskStatus.InProgress).failFast
        _ <- expect(movedTask.position == 1).failFast

      } yield success
    }
  }

  test("KanbanRepository should bulk move multiple tasks") {
    withKanbanRepository { kanbanRepo =>
      for {
        project <- ProjectGenerators.projectGen.sample

        task1 <- TaskGenerators.taskGen.map(_.copy(projectId = project.id)).sample
        task2 <- TaskGenerators.taskGen.map(_.copy(projectId = project.id)).sample

        moves = List(
          TaskMove(task1.id, TaskStatus.InProgress, 0),
          TaskMove(task2.id, TaskStatus.Done, 1),
        )

        // Bulk move tasks
        _ <- kanbanRepo.bulkMoveTask(moves)

        // Verify moves were applied
        board <- kanbanRepo.getKanbanBoard(project.id)
        inProgressTasks = board
          .columns
          .find(_.status == TaskStatus.InProgress)
          .map(_.tasks)
          .getOrElse(List.empty)
        doneTasks = board
          .columns
          .find(_.status == TaskStatus.Done)
          .map(_.tasks)
          .getOrElse(List.empty)

        _ <- expect(inProgressTasks.exists(_.id == task1.id)).failFast
        _ <- expect(doneTasks.exists(_.id == task2.id)).failFast

      } yield success
    }
  }

  test("KanbanRepository should reorder tasks within column") {
    withKanbanRepository { kanbanRepo =>
      for {
        project <- ProjectGenerators.projectGen.sample

        task1 <- TaskGenerators
          .taskGen
          .map(
            _.copy(
              projectId = project.id,
              status = TaskStatus.Todo,
              position = 0,
            )
          )
          .sample

        task2 <- TaskGenerators
          .taskGen
          .map(
            _.copy(
              projectId = project.id,
              status = TaskStatus.Todo,
              position = 1,
            )
          )
          .sample

        // Reorder tasks (swap positions)
        taskIds = List(task2.id, task1.id) // task2 first, then task1
        _ <- kanbanRepo.reorderTasksInColumn(project.id, TaskStatus.Todo, taskIds)

        // Verify new order
        tasks <- kanbanRepo.getTasksByStatus(project.id, TaskStatus.Todo)
        orderedTasks = tasks.sortBy(_.position)

        _ <- expect(orderedTasks.headOption.map(_.id).contains(task2.id)).failFast
        _ <- expect(orderedTasks.lift(1).map(_.id).contains(task1.id)).failFast

      } yield success
    }
  }

  private def withKanbanRepository[A](f: KanbanRepository[IO] => IO[A]): IO[A] =
    withRepositories(session => f(KanbanRepository.make[IO](session)))
}
