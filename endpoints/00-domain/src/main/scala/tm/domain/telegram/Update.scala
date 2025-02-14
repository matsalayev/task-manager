package tm.domain.telegram

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.refined._

@ConfiguredJsonCodec
case class Update(
    updateId: Int,
    message: Option[TelegramMessage],
    callbackQuery: Option[TelegramCallbackQuery],
  )

object Update {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
