package tm.repositories

import cats.data.OptionT
import cats.effect.Resource
import cats.implicits._
import skunk._

import tm.Language
import tm.ResponseMessages.USER_NOT_FOUND
import tm.domain.PersonId
import tm.effects.Calendar
import tm.exception.AError
import tm.repositories.sql.PeopleSql
import tm.support.skunk.syntax.all._

trait PeopleRepository[F[_]] {
  def create(person: dto.Person): F[Unit]
  def delete(id: PersonId): F[Unit]
  def get: F[List[dto.Person]]
  def findById(id: PersonId): F[Option[dto.Person]]
  def update(
      id: PersonId
    )(
      update: dto.Person => dto.Person
    )(implicit
      lang: Language
    ): F[Unit]
}

object PeopleRepository {
  def make[F[_]: fs2.Compiler.Target: Calendar](
      implicit
      session: Resource[F, Session[F]]
    ): PeopleRepository[F] = new PeopleRepository[F] {
    override def create(person: dto.Person): F[Unit] =
      PeopleSql.insert.execute(person)

    override def get: F[List[dto.Person]] =
      PeopleSql.get.all

    override def update(
        id: PersonId
      )(
        update: dto.Person => dto.Person
      )(implicit
        lang: Language
      ): F[Unit] =
      OptionT(PeopleSql.findById.queryOption(id))
        .cataF(
          AError.BadRequest(USER_NOT_FOUND(lang)).raiseError[F, Unit],
          people =>
            Calendar[F].currentZonedDateTime.flatMap { now =>
              PeopleSql.update.execute(update(people.copy(updatedAt = now.some)))
            },
        )

    override def findById(id: PersonId): F[Option[dto.Person]] =
      PeopleSql.findById.queryOption(id)

    override def delete(id: PersonId): F[Unit] =
      PeopleSql.delete.execute(id)
  }
}
