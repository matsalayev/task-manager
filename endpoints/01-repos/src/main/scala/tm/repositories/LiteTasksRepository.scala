package tm.repositories

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import tm.domain.{FolderId, ProjectId, TagId, TaskId}
import tm.domain.lite.{Folder, LiteTask}
import tm.domain.task.Tag
import tm.domain.task.Task
import tm.repositories.sql.{FoldersSql, LiteTasksSql, TagsSql, TasksSql}
import tm.support.skunk.syntax.all._

trait LiteTasksRepository[F[_]] {
  def create(task: LiteTask): F[Unit]
  def getAll(folderId: FolderId): F[List[LiteTask]]
  def findById(taskId: TaskId): F[Option[LiteTask]]
  def update(task: LiteTask): F[Unit]
  def delete(taskId: TaskId): F[Unit]
  def createFolder(folder: Folder): F[Unit]
  def getAllFolders(chatId: Long): F[List[Folder]]
  def deleteFolder(id: FolderId): F[Unit]
}

object LiteTasksRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): LiteTasksRepository[F] = new LiteTasksRepository[F] {
    override def create(Task: LiteTask): F[Unit] =
      LiteTasksSql.insert.execute(Task)

    override def getAll(folderId: FolderId): F[List[LiteTask]] =
      LiteTasksSql.getAll.queryList(folderId)

    override def findById(taskId: TaskId): F[Option[LiteTask]] =
      LiteTasksSql.findById.queryOption(taskId)

    override def update(task: LiteTask): F[Unit] =
      LiteTasksSql.update.execute(task)

    override def delete(taskId: TaskId): F[Unit] =
      LiteTasksSql.delete.execute(taskId)

    override def createFolder(folder: Folder): F[Unit] =
      FoldersSql.insert.execute(folder)

    override def getAllFolders(chatId: Long): F[List[Folder]] =
      FoldersSql.getAll.queryList(chatId)

    override def deleteFolder(id: FolderId): F[Unit] =
      FoldersSql.delete.execute(id)
  }
}
