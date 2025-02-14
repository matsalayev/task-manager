package tm.domain.enums

import enumeratum.EnumEntry.Snakecase
import enumeratum._

sealed trait WorkState extends Snakecase
object WorkState extends Enum[WorkState] with CirceEnum[WorkState] {
  case object InState extends WorkState
  case object OutOfState extends WorkState
  override def values: IndexedSeq[WorkState] = findValues
}
