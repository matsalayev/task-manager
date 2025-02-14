package tm.domain.enums

import enumeratum.EnumEntry.Snakecase
import enumeratum._

sealed trait WorkType extends Snakecase
object WorkType extends Enum[WorkType] with CirceEnum[WorkType] {
  case object FullTime extends WorkType
  case object Hourly extends WorkType
  override def values: IndexedSeq[WorkType] = findValues
}
