package tm.domain.enums

import enumeratum.EnumEntry.Snakecase
import enumeratum._

sealed trait WorkStatus extends Snakecase
object WorkStatus extends Enum[WorkStatus] with CirceEnum[WorkStatus] {
  case object Active extends WorkStatus
  case object Passive extends WorkStatus
  override def values: IndexedSeq[WorkStatus] = findValues
}
