package tm

import java.util.UUID

import scala.concurrent.duration.FiniteDuration

import cats.implicits.catsSyntaxEitherId
import derevo.cats.eqv
import derevo.cats.show
import derevo.derive
import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.macros.newtype
import pureconfig.BasicReaders.finiteDurationConfigReader
import pureconfig.ConfigReader
import pureconfig.error.FailureReason

import tm.syntax.refined.commonSyntaxAutoRefineV
import tm.utils.uuid

package object domain {
  @derive(eqv, show, uuid)
  @newtype case class PersonId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class AssetId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class LocationId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class CorporateId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class EmployeeId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class SpecialtyId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class ProjectId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class TaskId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class TagId(value: UUID)

  @newtype case class JwtAccessTokenKey(secret: NonEmptyString)

  object JwtAccessTokenKey {
    implicit val reader: ConfigReader[JwtAccessTokenKey] =
      ConfigReader.fromString[JwtAccessTokenKey](str =>
        JwtAccessTokenKey(str).asRight[FailureReason]
      )
  }

  @newtype case class TokenExpiration(value: FiniteDuration)

  object TokenExpiration {
    implicit val reader: ConfigReader[TokenExpiration] =
      finiteDurationConfigReader.map(duration => TokenExpiration(duration))
  }
}
