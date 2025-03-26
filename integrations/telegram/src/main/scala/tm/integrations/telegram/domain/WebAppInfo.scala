package tm.integrations.telegram.domain

import io.circe.generic.JsonCodec

@JsonCodec
case class WebAppInfo(
    url: String
  )
