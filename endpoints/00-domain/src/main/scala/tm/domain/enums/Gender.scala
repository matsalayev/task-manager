package tm.domain.enums

import enumeratum.EnumEntry.Snakecase
import enumeratum._

sealed trait Gender extends Snakecase
object Gender extends Enum[Gender] with CirceEnum[Gender] {
  case object Male extends Gender
  case object Female extends Gender
  override def values: IndexedSeq[Gender] = findValues
}
