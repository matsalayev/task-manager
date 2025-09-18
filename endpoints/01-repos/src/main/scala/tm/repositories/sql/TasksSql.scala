package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.codec.all._
import skunk.implicits._

import tm.domain.ProjectId
import tm.domain.TaskId
import tm.domain.task.Task
import tm.domain.task.TaskPriority
import tm.repositories.dto
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes
import tm.support.skunk.codecs.zonedDateTime

private[repositories] object TasksSql extends Sql[TaskId] {
  // TaskPriority codec
  private val taskPriority: Codec[TaskPriority] = varchar.imap[TaskPriority] {
    case "Low" => TaskPriority.Low
    case "Medium" => TaskPriority.Medium
    case "High" => TaskPriority.High
    case "Critical" => TaskPriority.Critical
    case other => throw new RuntimeException(s"Unknown priority: $other")
  } {
    case TaskPriority.Low => "Low"
    case TaskPriority.Medium => "Medium"
    case TaskPriority.High => "High"
    case TaskPriority.Critical => "Critical"
  }

  private val codec: Codec[Task] =
    (id *: zonedDateTime *: PeopleSql.id *: ProjectsSql.id *: nes *: nes.opt *:
      TagsSql.id.opt *: AssetsSql.id.opt *: taskStatus *: zonedDateTime.opt *: nes.opt *:
      taskPriority.opt *: int4 *: int4.opt *: zonedDateTime.opt)
      .to[Task]

  private val dtoCodec: Codec[dto.Task] =
    (id *: zonedDateTime *: nes *: ProjectsSql.id *: nes *: nes *: nes.opt *:
      nes.opt *: nes.opt *: AssetsSql.id.opt *: taskStatus *: zonedDateTime.opt *: nes.opt)
      .to[dto.Task]

  val insert: Command[Task] =
    sql"""INSERT INTO tasks (
      id, created_at, created_by, project_id, name, description, tag_id, asset_id,
      status, deadline, link, priority, position, estimated_hours, finished_at
    ) VALUES ($codec)""".command

  val getAll: Query[ProjectId, dto.Task] =
    sql"""
      SELECT
        t.id,
        t.created_at,
        p.full_name,
        t.project_id,
        pr.name,
        t.name,
        t.description,
        tag.name,
        tag.color,
        t.asset_id,
        t.status,
        t.deadline
      FROM tasks t
      INNER JOIN projects pr
        ON pr.id = t.project_id
      INNER JOIN tags tag
        ON tag.id = t.tag_id
      INNER JOIN employees e
        ON e.id = t.created_by
      INNER JOIN people p
        ON p.id = e.person_id
      WHERE t.project_id = ${ProjectsSql.id}
    """ query dtoCodec

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
        deadline = ${zonedDateTime.opt},
        priority = ${taskPriority.opt},
        position = $int4,
        estimated_hours = ${int4.opt},
        finished_at = ${zonedDateTime.opt}
      WHERE id = $id"""
      .command
      .contramap { (t: Task) =>
        t.name *: t.description *: t.tagId *: t.photo *: t.status *: t.deadline *:
          t.priority *: t.position *: t.estimatedHours *: t.finishedAt *: t.id *: EmptyTuple
      }

  val delete: Command[TaskId] =
    sql"""DELETE FROM tasks WHERE id = $id""".command
}
