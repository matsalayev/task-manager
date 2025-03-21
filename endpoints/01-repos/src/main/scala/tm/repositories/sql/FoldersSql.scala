package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.codec.all.int8
import skunk.implicits._

import tm.domain.FolderId
import tm.domain.TagId
import tm.domain.lite.Folder
import tm.domain.task.Tag
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes
import tm.support.skunk.codecs.zonedDateTime

private[repositories] object FoldersSql extends Sql[FolderId] {
  private[repositories] val codec: Codec[Folder] = (id *: zonedDateTime *: int8 *: nes).to[Folder]

  val insert: Command[Folder] =
    sql"""INSERT INTO folders VALUES ($codec)""".command

  def getAll(chatId: Long): AppliedFragment =
    sql"""SELECT *, COUNT(*) OVER() FROM folders WHERE user_id = $int8""".apply(chatId)

  val delete: Command[FolderId] =
    sql"""DELETE FROM folders WHERE id = $id""".command
}
