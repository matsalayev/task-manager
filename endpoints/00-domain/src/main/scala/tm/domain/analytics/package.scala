package tm.domain

import java.util.UUID

import derevo.cats.eqv
import derevo.cats.show
import derevo.derive
import io.estatico.newtype.macros.newtype

import tm.utils.uuid

package object analytics {
  @derive(eqv, show, uuid)
  @newtype case class DashboardDataId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class ProductivityInsightId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class GoalId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class TeamDashboardId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class ExecutiveDashboardId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class AnalyticsReportId(value: UUID)
}
