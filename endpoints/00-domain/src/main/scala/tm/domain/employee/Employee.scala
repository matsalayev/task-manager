package tm.domain.employee

import java.time.ZonedDateTime

import tm.Phone
import tm.domain.AssetId
import tm.domain.CorporateId
import tm.domain.EmployeeId
import tm.domain.PersonId
import tm.domain.RankId

case class Employee(
    id: EmployeeId,
    createdAt: ZonedDateTime,
    personId: PersonId,
    corporateId: CorporateId,
    rankId: RankId,
    photo: Option[AssetId],
    phone: Phone,
  )
