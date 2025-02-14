package tm.domain.asset

import java.time.ZonedDateTime

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.JsonCodec
import io.circe.refined._

import tm.domain.AssetId
import tm.syntax.circe._

@JsonCodec
case class Asset(
    id: AssetId,
    createdAt: ZonedDateTime,
    s3Key: NonEmptyString,
    fileName: Option[NonEmptyString],
    contentType: Option[NonEmptyString],
  )
