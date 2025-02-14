package tm.test

import cats.effect.IO
import weaver.Expectations

trait SimpleTestCase {
  def check: IO[Expectations]
}
