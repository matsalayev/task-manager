package tm.endpoint.routes

import cats.effect.kernel.Async
import cats.implicits._
import org.http4s._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.Http4sDsl

import tm.domain.TaskId
import tm.services.TaskContentService

case class TaskContentRoutes[F[_]: Async](taskContentService: TaskContentService[F])
    extends Http4sDsl[F] {
  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "tasks" / UUIDVar(taskId) / "comments" =>
      val taskIdDomain = TaskId(taskId)
      val mockUserId = tm.domain.PersonId(java.util.UUID.randomUUID())
      taskContentService.getComments(taskIdDomain, mockUserId).flatMap(comments => Ok(comments))

    case req @ POST -> Root / "tasks" / UUIDVar(taskId) / "comments" =>
      val taskIdDomain = TaskId(taskId)
      val mockUserId = tm.domain.PersonId(java.util.UUID.randomUUID())
      // Simple string content for now
      req.as[String].flatMap { content =>
        taskContentService
          .addComment(taskIdDomain, content, mockUserId)
          .flatMap(comment => Created(comment))
      }

    case GET -> Root / "tasks" / UUIDVar(taskId) / "attachments" =>
      val taskIdDomain = TaskId(taskId)
      val mockUserId = tm.domain.PersonId(java.util.UUID.randomUUID())
      taskContentService
        .getAttachments(taskIdDomain, mockUserId)
        .flatMap(attachments => Ok(attachments))
  }
}
