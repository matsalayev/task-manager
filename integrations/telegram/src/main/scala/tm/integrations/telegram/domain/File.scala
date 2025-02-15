package tm.integrations.telegram.domain

import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}

@ConfiguredJsonCodec
case class File(
    fileId: String,
    fileUniqueId: String,
    fileSize: Int,
    filePath: String,
  )

object File {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
