package tm.endpoint.routes

import cats.effect.kernel.Async
import cats.implicits._
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.Http4sDsl

import tm.domain.TaskId
import tm.domain.task.CreateDependencyRequest
import tm.domain.task.CreateSubtaskRequest
import tm.services.TaskDependencyService

case class TaskDependencyRoutes[F[_]: Async](dependencyService: TaskDependencyService[F])
    extends Http4sDsl[F] {
  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "tasks" / UUIDVar(taskId) / "dependencies" =>
      val taskIdDomain = TaskId(taskId)
      val mockUserId = tm.domain.PersonId(java.util.UUID.randomUUID())
      for {
        createRequest <- req.as[CreateDependencyRequest]
        dependency <- dependencyService.addDependency(
          taskIdDomain,
          createRequest.dependsOnTaskId,
          mockUserId,
        )
        response <- Created(dependency)
      } yield response

    case req @ POST -> Root / "tasks" / UUIDVar(taskId) / "subtasks" =>
      val taskIdDomain = TaskId(taskId)
      val mockUserId = tm.domain.PersonId(java.util.UUID.randomUUID())
      for {
        createRequest <- req.as[CreateSubtaskRequest]
        subtask <- dependencyService.addSubtask(taskIdDomain, createRequest.childTaskId, mockUserId)
        response <- Created(subtask)
      } yield response
  }
}
