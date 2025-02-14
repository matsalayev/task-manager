package tm.repositories

import skunk._
import skunk.codec.all._
import skunk.data.Type
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.SCrypt

import tm.Language
import tm.domain.enums._

package object sql {
  val gender: Codec[Gender] = `enum`[Gender](Gender, Type("gender"))
  val role: Codec[Role] = `enum`[Role](Role, Type("role"))
  val language: Codec[Language] = `enum`[Language](Language, Type("language"))
  val workState: Codec[WorkState] = `enum`[WorkState](WorkState, Type("work_state"))
  val workStatus: Codec[WorkStatus] = `enum`[WorkStatus](WorkStatus, Type("work_status"))
  val workType: Codec[WorkType] = `enum`[WorkType](WorkType, Type("work_type"))
  val taskStatus: Codec[TaskStatus] = `enum`[TaskStatus](TaskStatus, Type("task_status"))

  val passwordHash: Codec[PasswordHash[SCrypt]] =
    varchar.imap[PasswordHash[SCrypt]](PasswordHash[SCrypt])(identity)
}
