package tm.domain.enums

import enumeratum.EnumEntry.Snakecase
import enumeratum._

sealed trait BotType extends Snakecase
object BotType extends Enum[BotType] with CirceEnum[BotType] {
  case object Employee extends BotType
  case object Corporate extends BotType
  override def values: IndexedSeq[BotType] = findValues
}
