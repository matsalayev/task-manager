package tm.domain.corporate

import eu.timepit.refined.types.string.NonEmptyString
import tm.domain.LocationId

case class Location(
    id: LocationId,
    name: NonEmptyString,
    latitude: Double,
    longitude: Double,
  )
