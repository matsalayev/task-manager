package tm.domain.telegram

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
case class Message(
    messageId: Long,
    from: Option[User],
    text: Option[String],
    contact: Option[Contact],
    photo: Option[List[PhotoSize]],
    caption: Option[String],
    mediaGroupId: Option[String],
    location: Option[Location],
  )

object Message {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
