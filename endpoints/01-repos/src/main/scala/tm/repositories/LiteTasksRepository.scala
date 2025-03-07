package tm.repositories

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import skunk._

import tm.domain.ProjectId
import tm.domain.TagId
import tm.domain.TaskId
import tm.domain.task.Tag
import tm.domain.task.Task
import tm.repositories.sql.TagsSql
import tm.repositories.sql.TasksSql
import tm.support.skunk.syntax.all._

trait LiteTasksRepository[F[_]] {
  def create(task: Task): F[Unit]
  def getAll(projectId: ProjectId): F[List[dto.Task]]
  def findById(taskId: TaskId): F[Option[Task]]
  def findByName(name: NonEmptyString): F[Option[Task]]
  def update(task: Task): F[Unit]
  def delete(taskId: TaskId): F[Unit]
  def createTag(tag: Tag): F[Unit]
  def findTagById(tagId: TagId): F[Option[Tag]]
  def findTagByName(name: NonEmptyString): F[Option[Tag]]
  def deleteTag(tagId: TagId): F[Unit]
}

object LiteTasksRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): LiteTasksRepository[F] = new LiteTasksRepository[F] {
    override def create(Task: Task): F[Unit] =
      TasksSql.insert.execute(Task)

    override def getAll(projectId: ProjectId): F[List[dto.Task]] =
      TasksSql.getAll.queryList(projectId)

    override def findById(taskId: TaskId): F[Option[Task]] =
      TasksSql.findById.queryOption(taskId)

    override def findByName(name: NonEmptyString): F[Option[Task]] =
      TasksSql.findByName.queryOption(name)

    override def update(Task: Task): F[Unit] =
      TasksSql.update.execute(Task)

    override def delete(taskId: TaskId): F[Unit] =
      TasksSql.delete.execute(taskId)

    override def createTag(tag: Tag): F[Unit] =
      TagsSql.insert.execute(tag)

    override def findTagById(tagId: TagId): F[Option[Tag]] =
      TagsSql.findById.queryOption(tagId)

    override def findTagByName(name: NonEmptyString): F[Option[Tag]] =
      TagsSql.findByName.queryOption(name)

    override def deleteTag(tagId: TagId): F[Unit] =
      TagsSql.delete.execute(tagId)
  }
}
