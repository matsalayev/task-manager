package tm.repositories

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import skunk._

import tm.domain.CorporateId
import tm.domain.LocationId
import tm.domain.corporate.Corporate
import tm.domain.corporate.Location
import tm.repositories.sql.CorporationsSql
import tm.repositories.sql.LocationsSql
import tm.support.skunk.syntax.all._

trait CorporationsRepository[F[_]] {
  def create(corporate: Corporate): F[Unit]
  def findById(corporateId: CorporateId): F[Option[Corporate]]
  def findByName(name: NonEmptyString): F[Option[Corporate]]
  def update(corporate: Corporate): F[Unit]
  def delete(corporateId: CorporateId): F[Unit]
  def createLocation(location: Location): F[Unit]
  def findLocationById(locationId: LocationId): F[Option[Location]]
  def findLocationByName(name: NonEmptyString): F[Option[Location]]
  def updateLocation(location: Location): F[Unit]
  def deleteLocation(locationId: LocationId): F[Unit]
}

object CorporationsRepository {
  def make[F[_]: fs2.Compiler.Target](
      implicit
      session: Resource[F, Session[F]]
    ): CorporationsRepository[F] = new CorporationsRepository[F] {
    override def create(corporate: Corporate): F[Unit] =
      CorporationsSql.insert.execute(corporate)

    override def findById(corporateId: CorporateId): F[Option[Corporate]] =
      CorporationsSql.findById.queryOption(corporateId)

    override def findByName(name: NonEmptyString): F[Option[Corporate]] =
      CorporationsSql.findByName.queryOption(name)

    override def update(corporate: Corporate): F[Unit] =
      CorporationsSql.update.execute(corporate)

    override def delete(corporateId: CorporateId): F[Unit] =
      CorporationsSql.delete.execute(corporateId)

    override def createLocation(location: Location): F[Unit] =
      LocationsSql.insert.execute(location)

    override def findLocationById(locationId: LocationId): F[Option[Location]] =
      LocationsSql.findById.queryOption(locationId)

    override def findLocationByName(name: NonEmptyString): F[Option[Location]] =
      LocationsSql.findByName.queryOption(name)

    override def updateLocation(location: Location): F[Unit] =
      LocationsSql.update.execute(location)

    override def deleteLocation(locationId: LocationId): F[Unit] =
      LocationsSql.delete.execute(locationId)
  }
}
