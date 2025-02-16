package tm.domain.telegram

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

@ConfiguredJsonCodec
case class Location(
    latitude: Double,
    longitude: Double,
  )

object Location {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
