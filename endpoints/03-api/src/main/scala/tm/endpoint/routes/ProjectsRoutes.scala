package tm.endpoint.routes

import cats.MonadThrow
import cats.implicits._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.JsonDecoder
import org.typelevel.log4cats.Logger

import tm.domain.ProjectId
import tm.domain.auth.AuthedUser
import tm.domain.project.ProjectCreation
import tm.domain.project.ProjectUpdate
import tm.services.ProjectsService
import tm.support.http4s.utils.Routes
import tm.support.syntax.http4s.http4SyntaxReqOps

final case class ProjectsRoutes[F[_]: JsonDecoder: MonadThrow](
    projects: ProjectsService[F]
  )(implicit
    logger: Logger[F]
  ) extends Routes[F, AuthedUser] {
  override val path = "/projects"

  // Query parameters for pagination
  object LimitQueryParamMatcher extends QueryParamDecoderMatcher[Int]("limit")
  object PageQueryParamMatcher extends QueryParamDecoderMatcher[Int]("page")

  override val public: HttpRoutes[F] = HttpRoutes.empty

  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {
    // Create new project
    case ar @ POST -> Root as user =>
      ar.req.decodeR[ProjectCreation] { creation =>
        projects.createProject(creation, user.id).flatMap { projectId =>
          Created(Map("projectId" -> projectId.value.toString))
        }
      }

    // Get projects for company with pagination
    case GET -> Root :? LimitQueryParamMatcher(limit) +& PageQueryParamMatcher(page) as user =>
      projects.getProjects(user.corporateId, limit, page).flatMap(Ok(_))

    case GET -> Root as user =>
      projects.getProjects(user.corporateId).flatMap(Ok(_))

    // Get specific project by ID
    case GET -> Root / UUIDVar(projectId) as _ =>
      val targetProjectId = ProjectId(projectId)
      projects.getProjectById(targetProjectId).flatMap {
        case Some(project) => Ok(project)
        case None => NotFound("Project not found")
      }

    // Update project
    case ar @ PUT -> Root / UUIDVar(projectId) as _ =>
      val targetProjectId = ProjectId(projectId)
      ar.req.decodeR[ProjectUpdate] { update =>
        projects.updateProject(targetProjectId, update) *> NoContent()
      }

    // Delete project
    case DELETE -> Root / UUIDVar(projectId) as _ =>
      val targetProjectId = ProjectId(projectId)
      // TODO: Add permission check - only project creator or admin can delete
      projects.deleteProject(targetProjectId) *> NoContent()
  }
}
