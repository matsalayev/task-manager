package tm.integrations.telegram.domain

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
case class InlineKeyboardButton(
    text: String,
    callbackData: String,
  )

object InlineKeyboardButton {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
