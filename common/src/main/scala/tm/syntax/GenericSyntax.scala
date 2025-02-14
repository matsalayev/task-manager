package tm.syntax

import io.circe.Encoder
import io.circe.Printer
import io.circe.syntax.EncoderOps

trait GenericSyntax {
  implicit def genericSyntaxGenericTypeOps[A](obj: A): GenericTypeOps[A] =
    new GenericTypeOps[A](obj)

  implicit def genericSyntaxStringOps(s: String): StringOps =
    new StringOps(s)
}

final class GenericTypeOps[A](private val obj: A) {
  private val printer: Printer = Printer.spaces2.copy(dropNullValues = true)
  def toJson(implicit encoder: Encoder[A]): String = obj.asJson.printWith(printer)
}

final class StringOps(str: String) {
  def maskMiddlePart(
      charsLeft: Int = 2,
      charsRight: Int = 2,
      maskChar: String = "*",
    ): String =
    if (str.length > charsLeft + charsRight)
      str.take(charsLeft) + maskChar * (str.length - charsLeft - charsRight) + str.takeRight(
        charsRight
      )
    else
      maskChar * str.length
}
