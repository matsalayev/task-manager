package tm.repositories

import cats.effect.IO
import cats.effect.Resource
import cats.implicits.catsSyntaxOptionId
import cats.implicits.toFoldableOps
import skunk.Session
import tm.generators._
import tm.repositories.sql._
import tm.support.skunk.syntax.all.skunkSyntaxCommandOps

object data extends Generators with PeopleGenerators {
  object people {
    val person1: dto.Person = personGen
    val person2: dto.Person = personGen
    val values: List[dto.Person] = List(person1, person2)
  }

  def setup(implicit session: Resource[IO, Session[IO]]): IO[Unit] =
    setupPersons

  private def setupPersons(implicit session: Resource[IO, Session[IO]]): IO[Unit] =
    people.values.traverse_ { data =>
      PeopleSql.insert.execute(data)
    }
}
