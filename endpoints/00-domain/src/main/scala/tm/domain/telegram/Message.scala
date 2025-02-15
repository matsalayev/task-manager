package tm.domain.telegram

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
case class Message(
    messageId: Long,
    from: Option[User],
    senderChat: Option[Chat],
    text: Option[String],
    contact: Option[Contact],
    photo: Option[List[PhotoSize]],
    caption: Option[String],
    mediaGroupId: Option[String],
    chat: Option[Chat],
  )

object Message {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
