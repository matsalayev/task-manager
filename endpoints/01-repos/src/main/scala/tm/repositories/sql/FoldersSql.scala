package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.implicits._

import tm.domain.TagId
import tm.domain.task.Tag
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes

private[repositories] object FoldersSql extends Sql[TagId] {
  private val codec: Codec[Tag] = (id *: nes *: nes *: CorporationsSql.id).to[Tag]

  val insert: Command[Tag] =
    sql"""INSERT INTO tags VALUES ($codec)""".command

  val findById: Query[TagId, Tag] =
    sql"""SELECT * FROM tags WHERE id = $id LIMIT 1""".query(codec)

  val findByName: Query[NonEmptyString, Tag] =
    sql"""SELECT * FROM tags WHERE name = $nes LIMIT 1""".query(codec)

  val update: Command[Tag] =
    sql"""UPDATE tags
      SET
        name = $nes,
        color = $nes
      WHERE id = $id"""
      .command
      .contramap { (t: Tag) =>
        t.name *: t.color *: t.id *: EmptyTuple
      }

  val delete: Command[TagId] =
    sql"""DELETE FROM tags WHERE id = $id""".command
}
