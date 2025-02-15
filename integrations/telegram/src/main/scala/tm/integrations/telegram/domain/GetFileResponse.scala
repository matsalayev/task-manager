package tm.integrations.telegram.domain

import io.circe.generic.JsonCodec

@JsonCodec
case class GetFileResponse(
    ok: Boolean,
    result: Option[File],
  )
