package tm.endpoint.routes

import cats.MonadThrow
import cats.implicits._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.JsonDecoder
import org.typelevel.log4cats.Logger

import tm.domain.ProjectId
import tm.domain.TagId
import tm.domain.TaskId
import tm.domain.auth.AuthedUser
import tm.domain.task.CreateTag
import tm.domain.task.TaskAssignment
import tm.domain.task.TaskCreation
import tm.domain.task.TaskUpdate
import tm.services.TasksService
import tm.support.http4s.utils.Routes
import tm.support.syntax.http4s.http4SyntaxReqOps

final case class TasksRoutes[F[_]: JsonDecoder: MonadThrow](
    tasks: TasksService[F]
  )(implicit
    logger: Logger[F]
  ) extends Routes[F, AuthedUser] {
  override val path = "/tasks"

  override val public: HttpRoutes[F] = HttpRoutes.empty

  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {
    // Create new task
    case ar @ POST -> Root as user =>
      ar.req.decodeR[TaskCreation] { creation =>
        tasks.createTask(creation, user.id).flatMap { taskId =>
          Created(Map("taskId" -> taskId.value.toString))
        }
      }

    // Get tasks by project
    case GET -> Root / "project" / UUIDVar(projectId) as _ =>
      val targetProjectId = ProjectId(projectId)
      tasks.getTasksByProject(targetProjectId).flatMap(Ok(_))

    // Get specific task by ID
    case GET -> Root / UUIDVar(taskId) as _ =>
      val targetTaskId = TaskId(taskId)
      tasks.getTaskById(targetTaskId).flatMap {
        case Some(task) => Ok(task)
        case None => NotFound("Task not found")
      }

    // Update task
    case ar @ PUT -> Root / UUIDVar(taskId) as _ =>
      val targetTaskId = TaskId(taskId)
      ar.req.decodeR[TaskUpdate] { update =>
        tasks.updateTask(targetTaskId, update) *> NoContent()
      }

    // Delete task
    case DELETE -> Root / UUIDVar(taskId) as _ =>
      val targetTaskId = TaskId(taskId)
      // TODO: Add permission check - only task creator or project manager can delete
      tasks.deleteTask(targetTaskId) *> NoContent()

    // Assign task to users
    case ar @ POST -> Root / UUIDVar(taskId) / "assign" as _ =>
      val targetTaskId = TaskId(taskId)
      ar.req.decodeR[TaskAssignment] { assignment =>
        val assignmentWithTaskId = assignment.copy(taskId = targetTaskId)
        tasks.assignTask(assignmentWithTaskId) *> NoContent()
      }

    // Tag management endpoints
    case ar @ POST -> Root / "tags" as user =>
      ar.req.decodeR[CreateTag] { tag =>
        tasks.createTag(tag, user.corporateId) *> Created()
      }

    case GET -> Root / "tags" / UUIDVar(tagId) as _ =>
      val targetTagId = TagId(tagId)
      tasks.getTagById(targetTagId).flatMap {
        case Some(tag) => Ok(tag)
        case None => NotFound("Tag not found")
      }

    case DELETE -> Root / "tags" / UUIDVar(tagId) as _ =>
      val targetTagId = TagId(tagId)
      tasks.deleteTag(targetTagId) *> NoContent()
  }
}
