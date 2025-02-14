package tm.utils

import java.time.LocalDate

import scala.util.Try

object LocalDateVar {
  def unapply(str: String): Option[LocalDate] = {
    if (str.nonEmpty)
      Try(LocalDate.parse(str)).toOption
    else
      None
  }
}