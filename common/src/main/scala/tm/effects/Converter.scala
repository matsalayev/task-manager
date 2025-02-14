package tm.effects

import cats.MonadThrow
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import io.circe.generic.JsonCodec

import tm.syntax.all.circeSyntaxDecoderOps
trait Converter[F[_]] {
  def toCyrillic(str: String): F[String]
}

object Converter {
  @JsonCodec
  case class Mapping(letterCyrill: String, letterLatin: String)
  private val filePath = "mappings.json"

  def apply[F[_]](implicit ev: Converter[F]): Converter[F] = ev

  implicit def converter[F[_]: MonadThrow: FileLoader]: Converter[F] = new Converter[F] {
    lazy val loadMappings: F[List[Mapping]] =
      FileLoader[F].resourceAsString(filePath).flatMap(_.decodeAsF[F, List[Mapping]])

    override def toCyrillic(str: String): F[String] =
      loadMappings.map {
        _.foldLeft(str) { (updatedText, mapping) =>
          updatedText.replaceAll(mapping.letterLatin, mapping.letterCyrill)
        }
      }
  }
}
