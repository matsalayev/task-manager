package tm.repositories

import cats.effect.Resource
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.codec.all.int8

import tm.domain.CorporateId
import tm.domain.PaginatedResponse
import tm.domain.ProjectId
import tm.domain.project.Project
import tm.repositories.sql.ProjectsSql
import tm.support.skunk.syntax.all._

trait ProjectsRepository[F[_]] {
  def create(project: Project): F[Unit]
  def getAll(
      corporateId: CorporateId,
      limit: Int,
      page: Int,
    ): F[PaginatedResponse[Project]]
  def findById(projectId: ProjectId): F[Option[Project]]
  def findByName(name: NonEmptyString): F[Option[Project]]
  def update(project: Project): F[Unit]
  def delete(projectId: ProjectId): F[Unit]
}

object ProjectsRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): ProjectsRepository[F] = new ProjectsRepository[F] {
    override def create(project: Project): F[Unit] =
      ProjectsSql.insert.execute(project)

    override def getAll(
        corporateId: CorporateId,
        limit: Int,
        page: Int,
      ): F[PaginatedResponse[Project]] = {
      val af = ProjectsSql.getAll(corporateId).paginate(limit, page)
      for {
        data <- af.fragment.query(ProjectsSql.codec *: int8).queryList(af.argument)
        list = data.map(_.head)
        total = data.headOption.fold(0L)(_.tail.head)
      } yield PaginatedResponse(list, total)
    }

    override def findById(projectId: ProjectId): F[Option[Project]] =
      ProjectsSql.findById.queryOption(projectId)

    override def findByName(name: NonEmptyString): F[Option[Project]] =
      ProjectsSql.findByName.queryOption(name)

    override def update(project: Project): F[Unit] =
      ProjectsSql.update.execute(project)

    override def delete(projectId: ProjectId): F[Unit] =
      ProjectsSql.delete.execute(projectId)
  }
}
