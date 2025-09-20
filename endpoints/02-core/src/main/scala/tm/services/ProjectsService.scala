package tm.services

import cats.MonadThrow
import cats.implicits._

import tm.domain.CorporateId
import tm.domain.PaginatedResponse
import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.project.Project
import tm.domain.project.ProjectCreation
import tm.domain.project.ProjectUpdate
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.repositories.ProjectsRepository
import tm.utils.ID

trait ProjectsService[F[_]] {
  def createProject(creation: ProjectCreation, createdBy: PersonId): F[ProjectId]
  def getProjects(
      corporateId: CorporateId,
      limit: Int = 20,
      page: Int = 1,
    ): F[PaginatedResponse[Project]]
  def getProjectById(projectId: ProjectId): F[Option[Project]]
  def updateProject(projectId: ProjectId, update: ProjectUpdate): F[Unit]
  def deleteProject(projectId: ProjectId): F[Unit]
}

object ProjectsService {
  def make[F[_]: MonadThrow: Calendar: GenUUID](
      projectsRepository: ProjectsRepository[F]
    ): ProjectsService[F] =
    new ProjectsService[F] {
      override def createProject(creation: ProjectCreation, createdBy: PersonId): F[ProjectId] =
        for {
          projectId <- ID.make[F, ProjectId]
          now <- Calendar[F].currentZonedDateTime
          project = Project(
            id = projectId,
            createdAt = now,
            createdBy = createdBy,
            corporateId = creation.corporateId,
            name = creation.name,
            description = creation.description,
          )
          _ <- projectsRepository.create(project)
        } yield projectId

      override def getProjects(
          corporateId: CorporateId,
          limit: Int,
          page: Int,
        ): F[PaginatedResponse[Project]] =
        projectsRepository.getAll(corporateId, limit, page)

      override def getProjectById(projectId: ProjectId): F[Option[Project]] =
        projectsRepository.findById(projectId)

      override def updateProject(projectId: ProjectId, update: ProjectUpdate): F[Unit] =
        projectsRepository.findById(projectId).flatMap {
          case Some(project) =>
            val updatedProject = project.copy(
              name = update.name.getOrElse(project.name),
              description = update.description.orElse(project.description),
            )
            projectsRepository.update(updatedProject)
          case None =>
            MonadThrow[F].raiseError(new RuntimeException(s"Project with id $projectId not found"))
        }

      override def deleteProject(projectId: ProjectId): F[Unit] =
        projectsRepository.delete(projectId)
    }
}
