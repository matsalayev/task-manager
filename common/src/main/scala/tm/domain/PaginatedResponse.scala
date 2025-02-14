package tm.domain

import io.circe.generic.JsonCodec

import tm.syntax.circe._

@JsonCodec
case class PaginatedResponse[A](
    data: List[A],
    total: Long,
  )
