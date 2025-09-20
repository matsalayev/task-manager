package tm.endpoint.routes

import cats.effect.kernel.Async
import cats.implicits._
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.Http4sDsl

import tm.domain.ProjectId
import tm.domain.TaskId
import tm.domain.task.TaskMoveRequest
import tm.services.KanbanService

case class KanbanRoutes[F[_]: Async](kanbanService: KanbanService[F]) extends Http4sDsl[F] {
  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "projects" / UUIDVar(projectId) / "kanban" =>
      val projId = ProjectId(projectId)
      // Mock user for simplification - in real implementation this would come from auth
      val mockUserId = tm.domain.PersonId(java.util.UUID.randomUUID())
      kanbanService.getKanbanBoard(projId, mockUserId).flatMap(board => Ok(board))

    case req @ POST -> Root / "projects" / UUIDVar(_) / "tasks" / UUIDVar(
           taskId
         ) / "move" =>
      val taskIdDomain = TaskId(taskId)
      val mockUserId = tm.domain.PersonId(java.util.UUID.randomUUID())
      for {
        moveRequest <- req.as[TaskMoveRequest]
        movedTask <- kanbanService.moveTask(
          taskIdDomain,
          moveRequest.newStatus,
          moveRequest.newPosition,
          mockUserId,
        )
        response <- Ok(movedTask)
      } yield response
  }
}
