package tm.test

import cats.effect.IO
import weaver.Expectations

trait TestCase[Res] {
  def check(implicit dao: Res): IO[Expectations]
}
