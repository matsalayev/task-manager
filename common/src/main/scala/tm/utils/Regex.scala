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
}
