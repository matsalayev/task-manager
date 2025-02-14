package tm.repositories.sql

import skunk._
import skunk.codec.all.date
import skunk.implicits._
import tm.domain.PersonId
import tm.repositories.dto
import tm.support.skunk.Sql
import tm.support.skunk.codecs._

private[repositories] object PeopleSql extends Sql[PersonId] {
  private val columns =
    id *: zonedDateTime *: nes *: gender *: date.opt *: nes.opt *: nes.opt *: zonedDateTime.opt *: zonedDateTime.opt

  val codec: Codec[dto.Person] = columns.to[dto.Person]

  val insert: Command[dto.Person] =
    sql"""INSERT INTO people VALUES ($codec)""".command

  val get: Query[Void, dto.Person] =
    sql"""SELECT * FROM people WHERE  AND deleted_at IS NULL""".query(codec)

  val findById: Query[PersonId, dto.Person] =
    sql"""SELECT * FROM people WHERE id = $id AND deleted_at IS NULL LIMIT 1""".query(codec)

  val update: Command[dto.Person] =
    sql"""UPDATE people
      SET full_name = $nes,
       date_of_birth = ${date.opt},
       gender = $gender
      WHERE id = $id AND deleted_at IS NULL"""
      .command
      .contramap { (p: dto.Person) =>
        p.fullName *: p.dateOfBirth *: p.gender *: p.id *: EmptyTuple
      }

  val delete: Command[PersonId] =
    sql"""UPDATE people SET deleted_at = now() WHERE id = $id""".command
}
