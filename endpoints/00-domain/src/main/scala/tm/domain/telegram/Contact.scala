package tm.domain.telegram

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
case class Contact(
    phoneNumber: String,
    userId: Option[Long],
  )

object Contact {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
