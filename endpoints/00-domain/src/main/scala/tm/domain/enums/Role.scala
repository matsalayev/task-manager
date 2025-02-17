package tm.domain.enums

import enumeratum.EnumEntry.Snakecase
import enumeratum._

sealed trait Role extends Snakecase
object Role extends Enum[Role] with CirceEnum[Role] {
  case object Director extends Role
  case object Manager extends Role
  override def values: IndexedSeq[Role] = findValues
}
