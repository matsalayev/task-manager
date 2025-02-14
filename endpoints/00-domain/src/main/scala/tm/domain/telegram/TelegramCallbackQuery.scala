package tm.domain.telegram

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.refined._

@ConfiguredJsonCodec
case class TelegramCallbackQuery(
    from: Option[User],
    text: Option[String],
    message: Option[TelegramMessage],
    data: Option[NonEmptyString],
  )

object TelegramCallbackQuery {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
