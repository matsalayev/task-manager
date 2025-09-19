package tm.endpoint.routes.utils

import java.time.LocalDate

import org.http4s.QueryParamDecoder
import org.http4s.dsl.io._

object QueryParam {
  implicit val localDateQueryParamDecoder: QueryParamDecoder[LocalDate] =
    QueryParamDecoder[String].map(LocalDate.parse)

  // Required parameters
  object Date extends QueryParamDecoderMatcher[LocalDate]("date")
  object StartDate extends QueryParamDecoderMatcher[LocalDate]("startDate")
  object EndDate extends QueryParamDecoderMatcher[LocalDate]("endDate")

  // Optional parameters
  object OptionalDate extends OptionalQueryParamDecoderMatcher[LocalDate]("date")
  object OptionalStartDate extends OptionalQueryParamDecoderMatcher[LocalDate]("startDate")
  object OptionalEndDate extends OptionalQueryParamDecoderMatcher[LocalDate]("endDate")
  object OptionalPage extends OptionalQueryParamDecoderMatcher[Int]("page")
  object OptionalLimit extends OptionalQueryParamDecoderMatcher[Int]("limit")
  object OptionalOffset extends OptionalQueryParamDecoderMatcher[Int]("offset")
  object OptionalDays extends OptionalQueryParamDecoderMatcher[Int]("days")
  object OptionalPeriod extends OptionalQueryParamDecoderMatcher[String]("period")
  object OptionalIsRead extends OptionalQueryParamDecoderMatcher[Boolean]("isRead")
  object OptionalNotificationType extends OptionalQueryParamDecoderMatcher[String]("type")
  object OptionalPriority extends OptionalQueryParamDecoderMatcher[String]("priority")
  object QueryParam extends QueryParamDecoderMatcher[String]("q")
}
