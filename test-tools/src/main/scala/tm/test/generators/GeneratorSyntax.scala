package tm.test.generators

import cats.effect.Sync
import org.scalacheck.Gen
import org.scalacheck.Gen.option

trait GeneratorSyntax {
  implicit def genSyntax[T](generator: Gen[T]): GenSyntax[T] = new GenSyntax(generator)
  implicit def gen2instance[T](gen: Gen[T]): T = gen.sample.get
}

final class GenSyntax[T](generator: Gen[T]) {
  def sync[F[_]: Sync]: F[T] = Sync[F].delay(gen)
  def gen: T = generator.sample.get
  def genOpt: Option[T] = option(generator).sample.get
  def opt: Gen[Option[T]] = option(generator)
}
