package tm.repositories.sql

import eu.timepit.refined.types.string.NonEmptyString
import skunk._
import skunk.implicits._

import tm.domain.RankId
import tm.domain.employee.Rank
import tm.support.skunk.Sql
import tm.support.skunk.codecs.nes

private[repositories] object RanksSql extends Sql[RankId] {
  private val codec: Codec[Rank] = (id *: nes).to[Rank]

  val insert: Command[Rank] =
    sql"""INSERT INTO ranks VALUES ($codec)""".command

  val findById: Query[RankId, Rank] =
    sql"""SELECT * FROM ranks WHERE id = $id LIMIT 1""".query(codec)

  val findByName: Query[NonEmptyString, Rank] =
    sql"""SELECT * FROM ranks WHERE name = $nes LIMIT 1""".query(codec)

  val update: Command[Rank] =
    sql"""UPDATE ranks
      SET name = $nes
      WHERE id = $id"""
      .command
      .contramap { (r: Rank) =>
        r.name *: r.id *: EmptyTuple
      }

  val delete: Command[RankId] =
    sql"""DELETE FROM ranks WHERE id = $id""".command
}
