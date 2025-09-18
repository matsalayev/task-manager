package tm.domain

import java.util.UUID

import derevo.cats.eqv
import derevo.cats.show
import derevo.derive
import io.estatico.newtype.macros.newtype

import tm.utils.uuid

package object notifications {
  @derive(eqv, show, uuid)
  @newtype case class NotificationId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class NotificationTemplateId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class NotificationChannelId(value: UUID)

  @derive(eqv, show, uuid)
  @newtype case class NotificationRuleId(value: UUID)
}
