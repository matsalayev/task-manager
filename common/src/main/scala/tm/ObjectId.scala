package tm

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import tm.effects.IsUUID

case class ObjectId[A: IsUUID](
    id: A
  )

object ObjectId {
  implicit def entityEncoder[A: IsUUID: Encoder]: Encoder[ObjectId[A]] =
    deriveEncoder[ObjectId[A]]
}
