package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.codec.all.int8
import skunk.implicits._
import tm.domain.{FolderId, TagId}
import tm.domain.lite.Folder
import tm.domain.task.Tag
import tm.support.skunk.Sql
import tm.support.skunk.codecs.{nes, zonedDateTime}

private[repositories] object FoldersSql extends Sql[FolderId] {
  private val codec: Codec[Folder] = (id *: zonedDateTime *: int8 *: nes).to[Folder]

  val insert: Command[Folder] =
    sql"""INSERT INTO folders VALUES ($codec)""".command

  val getAll: Query[Long, Folder] =
    sql"""SELECT * FROM folders WHERE user_id = $int8""".query(codec)

  val delete: Command[FolderId] =
    sql"""DELETE FROM folders WHERE id = $id""".command
}
