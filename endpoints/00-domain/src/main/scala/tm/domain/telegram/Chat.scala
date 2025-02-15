package tm.domain.telegram

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
case class Chat(
    id: Long,
    `type`: String,
    title: Option[String],
    userName: Option[String],
  )

object Chat {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
