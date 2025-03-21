package tm.repositories

import cats.effect.Resource
import cats.implicits.toFunctorOps
import skunk._
import skunk.codec.all.int8

import tm.domain.FolderId
import tm.domain.PaginatedResponse
import tm.domain.TaskId
import tm.domain.lite.Folder
import tm.domain.lite.LiteTask
import tm.repositories.sql.FoldersSql
import tm.repositories.sql.LiteTasksSql
import tm.support.skunk.syntax.all._

trait LiteTasksRepository[F[_]] {
  def create(task: LiteTask): F[Unit]
  def getAll(
      folderId: FolderId,
      limit: Int,
      page: Int,
    ): F[PaginatedResponse[LiteTask]]
  def findById(taskId: TaskId): F[Option[LiteTask]]
  def update(task: LiteTask): F[Unit]
  def delete(taskId: TaskId): F[Unit]
  def createFolder(folder: Folder): F[Unit]
  def getAllFolders(
      chatId: Long,
      limit: Int,
      page: Int,
    ): F[PaginatedResponse[Folder]]
  def deleteFolder(id: FolderId): F[Unit]
}

object LiteTasksRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): LiteTasksRepository[F] = new LiteTasksRepository[F] {
    override def create(Task: LiteTask): F[Unit] =
      LiteTasksSql.insert.execute(Task)

    override def getAll(
        folderId: FolderId,
        limit: Int,
        page: Int,
      ): F[PaginatedResponse[LiteTask]] = {
      val query = LiteTasksSql.getAll(folderId).paginate(limit, page)
      for {
        data <- query.fragment.query(LiteTasksSql.codec *: int8).queryList(query.argument)
        list = data.map(_.head)
        total = data.headOption.fold(0L)(_.tail.head)
      } yield PaginatedResponse(list, total)
    }

    override def findById(taskId: TaskId): F[Option[LiteTask]] =
      LiteTasksSql.findById.queryOption(taskId)

    override def update(task: LiteTask): F[Unit] =
      LiteTasksSql.update.execute(task)

    override def delete(taskId: TaskId): F[Unit] =
      LiteTasksSql.delete.execute(taskId)

    override def createFolder(folder: Folder): F[Unit] =
      FoldersSql.insert.execute(folder)

    override def getAllFolders(
        chatId: Long,
        limit: Int,
        page: Int,
      ): F[PaginatedResponse[Folder]] = {
      val query = FoldersSql.getAll(chatId).paginate(limit, page)
      for {
        data <- query.fragment.query(FoldersSql.codec *: int8).queryList(query.argument)
        list = data.map(_.head)
        total = data.headOption.fold(0L)(_.tail.head)
      } yield PaginatedResponse(list, total)
    }

    override def deleteFolder(id: FolderId): F[Unit] =
      FoldersSql.delete.execute(id)
  }
}
