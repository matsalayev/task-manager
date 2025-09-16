package tm.domain.task

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.refined._

case class CreateTag(
    name: NonEmptyString,
    color: NonEmptyString,
  )

object CreateTag {
  implicit val codec: Codec[CreateTag] = deriveCodec
}
