package tm.repositories.sql

import skunk._
import skunk.codec.all.int8
import skunk.implicits._

import tm.domain.FolderId
import tm.domain.TaskId
import tm.domain.lite.LiteTask
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes
import tm.support.skunk.codecs.zonedDateTime

private[repositories] object LiteTasksSql extends Sql[TaskId] {
  private val codec: Codec[LiteTask] =
    (id *: zonedDateTime *: int8 *: FoldersSql.id *: nes *: taskStatus *: zonedDateTime.opt *: zonedDateTime.opt *: int8)
      .to[LiteTask]

  val insert: Command[LiteTask] =
    sql"""INSERT INTO lite_tasks VALUES ($codec)""".command

  val getAll: Query[FolderId, LiteTask] =
    sql"""
      SELECT *
      FROM lite_tasks t
      WHERE t.folder_id = ${FoldersSql.id}
    """.query(codec)

  val findById: Query[TaskId, LiteTask] =
    sql"""SELECT * FROM lite_tasks WHERE id = $id LIMIT 1""".query(codec)

  val update: Command[LiteTask] =
    sql"""UPDATE lite_tasks
      SET
        name = $nes,
        status = $taskStatus,
        started_at = ${zonedDateTime.opt}
        finished_at = ${zonedDateTime.opt}
      WHERE id = $id"""
      .command
      .contramap { (t: LiteTask) =>
        t.name *: t.status *: t.startedAt *: t.finishedAt *: t.id *: EmptyTuple
      }

  val delete: Command[TaskId] =
    sql"""DELETE FROM lite_tasks WHERE id = $id""".command
}
