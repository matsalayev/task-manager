package tm.domain.corporate

import tm.Phone
import tm.domain.AssetId
import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.enums.Role

case class User(
    id: PersonId,
    role: Role,
    phone: Phone,
    asset_id: Option[AssetId],
    corporate_id: CorporateId,
  )
