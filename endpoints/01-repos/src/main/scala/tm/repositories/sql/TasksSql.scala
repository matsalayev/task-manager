package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.implicits._

import tm.domain.TaskId
import tm.domain.task.Task
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes
import tm.support.skunk.codecs.zonedDateTime

private[repositories] object TasksSql extends Sql[TaskId] {
  private val codec: Codec[Task] =
    (id *: zonedDateTime *: EmployeesSql.id *: ProjectsSql.id *: nes *: nes.opt *:
      TagsSql.id.opt *: AssetsSql.id.opt *: taskStatus *: zonedDateTime.opt)
      .to[Task]

  val insert: Command[Task] =
    sql"""INSERT INTO tasks VALUES ($codec)""".command

  val findById: Query[TaskId, Task] =
    sql"""SELECT * FROM tasks WHERE id = $id LIMIT 1""".query(codec)

  val findByName: Query[NonEmptyString, Task] =
    sql"""SELECT * FROM tasks WHERE name = $nes LIMIT 1""".query(codec)

  val update: Command[Task] =
    sql"""UPDATE tasks
      SET
        name = $nes,
        description = ${nes.opt},
        tag_id = ${TagsSql.id.opt},
        asset_id = ${AssetsSql.id.opt},
        status = $taskStatus,
        deadline = ${zonedDateTime.opt}
      WHERE id = $id"""
      .command
      .contramap { (t: Task) =>
        t.name *: t.description *: t.tagId *: t.photo *: t.status *: t.deadline *: t.id *: EmptyTuple
      }

  val delete: Command[TaskId] =
    sql"""DELETE FROM tasks WHERE id = $id""".command
}
