package tm.integrations.telegram.domain

import io.circe.generic.JsonCodec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
sealed trait ReplyMarkup

object ReplyMarkup {
  @JsonCodec
  case class ReplyKeyboardMarkup(
      keyboard: List[List[KeyboardButton]]
    ) extends ReplyMarkup

  @ConfiguredJsonCodec
  case class ReplyInlineKeyboardMarkup(
      inlineKeyboard: List[List[InlineKeyboardButton]],
    ) extends ReplyMarkup

  @ConfiguredJsonCodec
  case class ReplyKeyboardRemove(
      removeKeyboard: Boolean = true
    ) extends ReplyMarkup

  object ReplyKeyboardRemove {
    implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
  }

  object ReplyInlineKeyboardMarkup {
    implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
  }

  implicit val configuration: Configuration = Configuration.default.withDiscriminator("type")
}
