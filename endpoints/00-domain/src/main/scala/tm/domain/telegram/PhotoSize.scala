package tm.domain.telegram

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
case class PhotoSize(
    fileId: String,
    width: Int,
    height: Int,
    fileSize: Option[Int],
  )

object PhotoSize {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
