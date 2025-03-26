package tm.utils

import scala.util.matching.Regex

object Regex {
  val regexDigits: Regex = """(\d+)_(\d+)""".r
  val regexActionWithInfo: Regex = """(\S+)_(.+)""".r
  val regexActionWithPage: Regex = """(\S+)_(\d+)""".r
  val regexActionsWithPage: Regex = """(\S+)_(\S+)_(\d+)""".r
  val regexUUIDWithActionAndPage: Regex =
    """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})_(\S+)_(\d+)""".r
  val regexActionAndUUIDWithDigits: Regex =
    """(\S+)_([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})_(\d+)_(\d+)""".r
  val regexUUIDWithActionAndDigits: Regex =
    """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})_(\S+)_(\d+)_(\d+)""".r
  val regexUUIDWithActionAndDigitsAndPage: Regex =
    """([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})_(\S+)_(\d+)_(\d+)_(\d+)""".r
  val regexFullName: Regex = """([\p{L}]+)\s+([\p{L}]+)""".r
  val regexDateOfBirth: Regex = """((19|20)\d{2}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01]))""".r
  val regexDocumentNumber: Regex = """(^[A-Z]{2}\d{7})""".r
  val regexPinfl: Regex = """(\d{14})""".r
  val regexCompanyName: Regex = """'([^']*)'""".r
}
