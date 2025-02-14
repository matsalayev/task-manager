package tm

import io.circe.generic.JsonCodec

@JsonCodec(encodeOnly = true)
case class SuccessResult(
    message: String
  )
