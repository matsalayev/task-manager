package tm.domain.enums

import enumeratum.EnumEntry.Snakecase
import enumeratum._

sealed trait TaskStatus extends Snakecase
object TaskStatus extends Enum[TaskStatus] with CirceEnum[TaskStatus] {
  case object ToDo extends TaskStatus
  case object InProgress extends TaskStatus
  case object InReview extends TaskStatus
  case object Testing extends TaskStatus
  case object Done extends TaskStatus
  override def values: IndexedSeq[TaskStatus] = findValues
}
