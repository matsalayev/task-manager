package tm.services

import cats.MonadThrow
import cats.implicits._

import tm.domain.CorporateId
import tm.domain.ProjectId
import tm.domain.TagId
import tm.domain.TaskId
import tm.domain.task.CreateTag
import tm.domain.task.CreateTask
import tm.domain.task.Tag
import tm.domain.task.Task
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.repositories.TasksRepository
import tm.repositories.dto
import tm.syntax.refined._
import tm.utils.ID

trait TasksService[F[_]] {
//  def create(task: CreateTask, createdBy: EmployeeId): F[Unit]
  def createTag(tag: CreateTag, corporateId: CorporateId): F[Unit]
  def getAllTasks(projectId: ProjectId): F[List[dto.Task]]
}

object TasksService {
  def make[F[_]: MonadThrow: GenUUID: Calendar](
      tasksRepository: TasksRepository[F]
    ): TasksService[F] =
    new TasksService[F] {
//      override def create(data: CreateTask, createdBy: EmployeeId): F[Unit] = for {
//        id <- ID.make[F, TaskId]
//        now <- Calendar[F].currentZonedDateTime
//        _ <- tasksRepository.create(
//          Task(
//            id = id,
//            createdAt = now,
//            createdBy = createdBy,
//            projectId = data.projectId,
//            name = data.name,
//            description = data.description,
//            tagId = data.tagId,
//            photo = data.photo,
//            status = data.status,
//            deadline = data.deadline,
//          )
//        )
//      } yield ()
      override def createTag(data: CreateTag, corporateId: CorporateId): F[Unit] = for {
        id <- ID.make[F, TagId]
        _ <- tasksRepository.createTag(
          Tag(id = id, name = data.name, color = data.color.some, corporateId = corporateId)
        )
      } yield ()

      override def getAllTasks(projectId: ProjectId): F[List[dto.Task]] =
        tasksRepository.getAll(projectId)
    }
}
