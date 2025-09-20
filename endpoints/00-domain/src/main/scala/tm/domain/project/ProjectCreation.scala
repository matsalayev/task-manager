package tm.domain.project

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.refined._

import tm.domain.CorporateId
import tm.syntax.circe._

case class ProjectCreation(
    name: NonEmptyString,
    description: Option[NonEmptyString],
    corporateId: CorporateId,
  )

object ProjectCreation {
  implicit val codec: Codec[ProjectCreation] = deriveCodec
}

case class ProjectUpdate(
    name: Option[NonEmptyString] = None,
    description: Option[NonEmptyString] = None,
  )

object ProjectUpdate {
  implicit val codec: Codec[ProjectUpdate] = deriveCodec
}
