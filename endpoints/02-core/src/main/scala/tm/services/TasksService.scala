package tm.services

import cats.MonadThrow
import cats.implicits._

import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.TagId
import tm.domain.TaskId
import tm.domain.task.CreateTag
import tm.domain.task.Tag
import tm.domain.task.Task
import tm.domain.task.TaskAssignment
import tm.domain.task.TaskCreation
import tm.domain.task.TaskUpdate
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.repositories.TasksRepository
import tm.syntax.refined._
import tm.utils.ID

trait TasksService[F[_]] {
  def createTask(creation: TaskCreation, createdBy: PersonId): F[TaskId]
  def getTasksByProject(projectId: ProjectId): F[List[Task]]
  def getTaskById(taskId: TaskId): F[Option[Task]]
  def updateTask(taskId: TaskId, update: TaskUpdate): F[Unit]
  def deleteTask(taskId: TaskId): F[Unit]
  def assignTask(assignment: TaskAssignment): F[Unit]
  def createTag(tag: CreateTag, corporateId: CorporateId): F[Unit]
  def getTagById(tagId: TagId): F[Option[Tag]]
  def deleteTag(tagId: TagId): F[Unit]
}

object TasksService {
  def make[F[_]: MonadThrow: GenUUID: Calendar](
      tasksRepository: TasksRepository[F]
    ): TasksService[F] =
    new TasksService[F] {
      override def createTask(creation: TaskCreation, createdBy: PersonId): F[TaskId] =
        for {
          taskId <- ID.make[F, TaskId]
          now <- Calendar[F].currentZonedDateTime
          task = Task(
            id = taskId,
            createdAt = now,
            createdBy = createdBy,
            projectId = creation.projectId,
            name = creation.name,
            description = creation.description,
            tagId = creation.tagId,
            photo = creation.photo,
            status = creation.status,
            deadline = creation.deadline,
            link = creation.link,
          )
          _ <- tasksRepository.create(task)
          // TODO: Handle task assignment to multiple assignees
          // This would require separate AssigneeRepository and logic
        } yield taskId

      override def getTasksByProject(projectId: ProjectId): F[List[Task]] =
        for {
          dtoTasks <- tasksRepository.getAll(projectId)
          domainTasks <- dtoTasks
            .traverse(dtoTask => tasksRepository.findById(dtoTask.id))
            .map(_.flatten)
        } yield domainTasks

      override def getTaskById(taskId: TaskId): F[Option[Task]] =
        tasksRepository.findById(taskId)

      override def updateTask(taskId: TaskId, update: TaskUpdate): F[Unit] =
        tasksRepository.findById(taskId).flatMap {
          case Some(task) =>
            val updatedTask = task.copy(
              name = update.name.getOrElse(task.name),
              description = update.description.orElse(task.description),
              tagId = update.tagId.orElse(task.tagId),
              photo = update.photo.orElse(task.photo),
              status = update.status.getOrElse(task.status),
              deadline = update.deadline.orElse(task.deadline),
              link = update.link.orElse(task.link),
            )
            tasksRepository.update(updatedTask)
          case None =>
            MonadThrow[F].raiseError(new RuntimeException(s"Task with id $taskId not found"))
        }

      override def deleteTask(taskId: TaskId): F[Unit] =
        tasksRepository.delete(taskId)

      override def assignTask(assignment: TaskAssignment): F[Unit] =
        // TODO: This needs to be implemented with proper AssigneeRepository
        // For now, just validate the task exists
        tasksRepository.findById(assignment.taskId).flatMap {
          case Some(_) => ().pure[F] // Task exists, assignment would be stored in assignees table
          case None =>
            MonadThrow[F]
              .raiseError(new RuntimeException(s"Task with id ${assignment.taskId} not found"))
        }

      override def createTag(data: CreateTag, corporateId: CorporateId): F[Unit] = for {
        id <- ID.make[F, TagId]
        _ <- tasksRepository.createTag(
          Tag(id = id, name = data.name, color = data.color.some, corporateId = corporateId)
        )
      } yield ()

      override def getTagById(tagId: TagId): F[Option[Tag]] =
        tasksRepository.findTagById(tagId)

      override def deleteTag(tagId: TagId): F[Unit] =
        tasksRepository.deleteTag(tagId)
    }
}
