package tm.domain.telegram

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.refined._

@ConfiguredJsonCodec
case class CallbackQuery(
    from: Option[User],
    text: Option[String],
    message: Option[Message],
    data: Option[NonEmptyString],
  )

object CallbackQuery {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
