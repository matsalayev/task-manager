package tm.domain.corporate

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString

import tm.domain.AssetId
import tm.domain.CorporateId
import tm.domain.LocationId

case class Corporate(
    id: CorporateId,
    createdAt: ZonedDateTime,
    name: NonEmptyString,
    locationId: LocationId,
    photo: Option[AssetId],
  )
