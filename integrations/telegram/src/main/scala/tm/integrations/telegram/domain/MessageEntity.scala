package tm.integrations.telegram.domain

import io.circe.generic.JsonCodec

@JsonCodec
case class MessageEntity(
    `type`: MessageEntityType,
    offset: Int,
    length: Int,
    url: Option[String] = None,
  )
