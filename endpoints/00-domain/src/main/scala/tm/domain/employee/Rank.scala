package tm.domain.employee

import eu.timepit.refined.types.string.NonEmptyString
import tm.domain.RankId

case class Rank(
    id: RankId,
    name: NonEmptyString,
  )
