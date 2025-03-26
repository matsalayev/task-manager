package tm.integrations.telegram.domain

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
case class InlineKeyboardButton(
    text: String,
    callbackData: Option[String],
    webApp: Option[WebAppInfo] = None,
    url: Option[String] = None,
  )

object InlineKeyboardButton {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
