package tm.support.skunk

import skunk.Codec
import tm.effects.IsUUID
import tm.support.skunk.codecs.identification

abstract class Sql[T: IsUUID] {
  val id: Codec[T] = identification[T]
}
