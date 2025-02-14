package tm.syntax

import java.net.URI
import java.net.URL

import cats.MonadThrow
import cats.data.EitherT
import io.circe._
import io.circe.parser.decode
import io.estatico.newtype.Coercible
import io.estatico.newtype.ops._
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.SCrypt

trait CirceSyntax {
  implicit def circeSyntaxDecoderOps(s: String): DecoderOps = new DecoderOps(s)
  implicit def circeSyntaxJsonDecoderOps(json: Json): JsonDecoderOps = new JsonDecoderOps(json)

  implicit def coercibleDecoder[A: Coercible[B, *], B: Decoder]: Decoder[A] =
    Decoder[B].map(_.coerce[A])

  implicit def coercibleEncoder[A: Coercible[B, *], B: Encoder]: Encoder[A] =
    Encoder[B].contramap(_.asInstanceOf[B])

  implicit def coercibleKeyDecoder[A: Coercible[B, *], B: KeyDecoder]: KeyDecoder[A] =
    KeyDecoder[B].map(_.coerce[A])

  implicit def coercibleKeyEncoder[A: Coercible[B, *], B: KeyEncoder]: KeyEncoder[A] =
    KeyEncoder[B].contramap[A](_.asInstanceOf[B])

  implicit val passwordHashEncoder: Encoder[PasswordHash[SCrypt]] =
    Encoder.encodeString.contramap(_.toString)
  implicit val passwordHashDecoder: Decoder[PasswordHash[SCrypt]] =
    Decoder.decodeString.map(PasswordHash[SCrypt])

  implicit val urlEncoder: Encoder[URL] =
    Encoder.encodeString.contramap(_.toString)
  implicit val urlDecoder: Decoder[URL] =
    Decoder.decodeString.map(URI.create(_).toURL)
}

final class DecoderOps(private val s: String) {
  def decodeAs[A: Decoder]: A = decode[A](s).fold(throw _, json => json)
  def decodeAsF[F[_]: MonadThrow, A: Decoder]: F[A] =
    EitherT.fromEither[F](decode[A](s)).rethrowT
}
final class JsonDecoderOps(json: Json) {
  def decodeAs[A](implicit decoder: Decoder[A]): A =
    decoder.decodeJson(json).fold(throw _, json => json)
  def decodeAsF[F[_]: MonadThrow, A](implicit decoder: Decoder[A]): F[A] =
    EitherT.fromEither[F](decoder.decodeJson(json)).rethrowT
}
