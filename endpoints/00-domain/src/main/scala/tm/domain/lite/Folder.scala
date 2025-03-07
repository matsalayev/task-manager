package tm.domain.lite

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.FolderId

case class Folder(
    id: FolderId,
    createdAt: ZonedDateTime,
    userId: Long,
    name: NonEmptyString,
  )
