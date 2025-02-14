package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.implicits._

import tm.domain.CorporateId
import tm.domain.ProjectId
import tm.domain.project.Project
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes
import tm.support.skunk.codecs.zonedDateTime

private[repositories] object ProjectsSql extends Sql[ProjectId] {
  private val codec: Codec[Project] =
    (id *: zonedDateTime *: EmployeesSql.id *: CorporationsSql.id *: nes *: nes.opt)
      .to[Project]

  val insert: Command[Project] =
    sql"""INSERT INTO projects VALUES ($codec)""".command

  val getAll: Query[CorporateId, Project] =
    sql"""SELECT * FROM projects WHERE corporate_id = ${CorporationsSql.id}""".query(codec)

  val findById: Query[ProjectId, Project] =
    sql"""SELECT * FROM projects WHERE id = $id LIMIT 1""".query(codec)

  val findByName: Query[NonEmptyString, Project] =
    sql"""SELECT * FROM projects WHERE name = $nes LIMIT 1""".query(codec)

  val update: Command[Project] =
    sql"""UPDATE projects
      SET
        name = $nes,
        description = ${nes.opt}
      WHERE id = $id"""
      .command
      .contramap { (p: Project) =>
        p.name *: p.description *: p.id *: EmptyTuple
      }

  val delete: Command[ProjectId] =
    sql"""DELETE FROM projects WHERE id = $id""".command
}
