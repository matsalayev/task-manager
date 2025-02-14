package tm.domain.telegram

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
case class TelegramMessage(
    messageId: Long,
    from: Option[User],
    text: Option[String],
    contact: Option[Contact],
  )

object TelegramMessage {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
