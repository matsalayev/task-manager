package tm.domain.telegram

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.refined._

@ConfiguredJsonCodec
case class User(
    id: Long,
    firstName: NonEmptyString,
    lastName: Option[NonEmptyString],
    username: Option[NonEmptyString],
  )

object User {
  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames
}
