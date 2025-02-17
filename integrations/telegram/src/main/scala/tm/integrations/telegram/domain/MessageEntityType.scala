package tm.integrations.telegram.domain

import enumeratum.EnumEntry.Snakecase
import enumeratum._

sealed trait MessageEntityType extends Snakecase

object MessageEntityType extends Enum[MessageEntityType] with CirceEnum[MessageEntityType] {
  case object Pre extends MessageEntityType
  case object Strikethrough extends MessageEntityType
  case object Spoiler extends MessageEntityType
  case object Bold extends MessageEntityType
  case object Italic extends MessageEntityType
  case object Blockquote extends MessageEntityType
  case object ExpandableBlockquote extends MessageEntityType
  case object Code extends MessageEntityType

  override def values: IndexedSeq[MessageEntityType] = findValues
}
