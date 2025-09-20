package tm.domain.task

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.refined._

import tm.domain.CorporateId
import tm.domain.TagId
import tm.syntax.circe._

case class Tag(
    id: TagId,
    name: NonEmptyString,
    color: Option[NonEmptyString],
    corporateId: CorporateId,
  )

object Tag {
  implicit val codec: Codec[Tag] = deriveCodec
}
