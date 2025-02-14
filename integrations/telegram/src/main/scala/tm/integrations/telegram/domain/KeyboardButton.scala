package tm.integrations.telegram.domain

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
case class KeyboardButton(
    text: String,
    requestContact: Boolean = false,
  )

object KeyboardButton {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
