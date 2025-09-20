package tm.endpoint.routes

import cats.effect.Async
import cats.implicits._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._

import tm.domain.auth.AuthedUser
import tm.domain.time._
import tm.effects.Calendar
import tm.endpoint.routes.utils.QueryParam._
import tm.services.TimeTrackingService
import tm.support.http4s.utils.Routes

final case class TimeTrackingRoutes[F[_]: Async](
    timeService: TimeTrackingService[F]
  ) extends Routes[F, AuthedUser] {
  override val path: String = "/time"
  override val public: HttpRoutes[F] = HttpRoutes.empty[F]
  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {

    // Work Session endpoints
    case req @ POST -> Root / "sessions" / "start" as user =>
      for {
        request <- req.req.as[StartWorkSessionRequest]
        session <- timeService.startWorkSession(user.id, request)
        response <- Ok(session)
      } yield response

    case POST -> Root / "sessions" / "end" as user =>
      for {
        sessionOpt <- timeService.endWorkSession(user.id)
        response <- sessionOpt match {
          case Some(session) => Ok(session)
          case None => NotFound("No active work session found")
        }
      } yield response

    case GET -> Root / "sessions" / "current" as user =>
      for {
        sessionOpt <- timeService.getCurrentSession(user.id)
        response <- sessionOpt match {
          case Some(session) => Ok(session)
          case None => NotFound("No active work session")
        }
      } yield response

    case GET -> Root / "sessions" :? OptionalPage(page) +& OptionalLimit(limit) as user =>
      for {
        sessions <- timeService.getWorkSessions(user.id, limit.getOrElse(20), page.getOrElse(1))
        response <- Ok(sessions)
      } yield response

    // Time Entry endpoints
    case req @ POST -> Root / "entries" / "start" as user =>
      for {
        request <- req.req.as[StartTimerRequest]
        entry <- timeService.startTimer(user.id, request)
        response <- Ok(entry)
      } yield response

    case POST -> Root / "entries" / UUIDVar(entryId) / "end" as user =>
      for {
        entryOpt <- timeService.endTimer(user.id, TimeEntryId(entryId))
        response <- entryOpt match {
          case Some(entry) => Ok(entry)
          case None => NotFound("Time entry not found or not running")
        }
      } yield response

    case req @ POST -> Root / "break" / "start" as user =>
      for {
        request <- req.req.as[StartBreakRequest]
        entry <- timeService.startBreak(user.id, request)
        response <- Ok(entry)
      } yield response

    case POST -> Root / "break" / "end" as user =>
      for {
        entryOpt <- timeService.endBreak(user.id)
        response <- entryOpt match {
          case Some(entry) => Ok(entry)
          case None => NotFound("No active break found")
        }
      } yield response

    case req @ POST -> Root / "entries" / "manual" as user =>
      for {
        request <- req.req.as[ManualTimeEntryRequest]
        entry <- timeService.addManualEntry(user.id, request)
        response <- Ok(entry)
      } yield response

    case GET -> Root / "entries" as user =>
      for {
        entries <- timeService.getTimeEntries(user.id, None)
        response <- Ok(entries)
      } yield response

    case req @ POST -> Root / "entries" / "filter" as user =>
      for {
        filter <- req.req.as[TimeEntriesFilterRequest]
        entries <- timeService.getTimeEntries(user.id, filter.taskId)
        response <- Ok(entries)
      } yield response

    case GET -> Root / "entries" / "running" as user =>
      for {
        entries <- timeService.getRunningEntries(user.id)
        response <- Ok(entries)
      } yield response

    // Dashboard and Reports endpoints
    case GET -> Root / "dashboard" as user =>
      for {
        dashboard <- timeService.getTimeDashboard(user.id)
        response <- Ok(dashboard)
      } yield response

    case GET -> Root / "reports" / "daily" :? OptionalDate(date) as user =>
      for {
        today <- Calendar[F].currentDate
        reportDate = date.getOrElse(today)
        report <- timeService.getDailyReport(user.id, reportDate)
        response <- Ok(report)
      } yield response

    case GET -> Root / "reports" / "weekly" :? OptionalStartDate(weekStart) as user =>
      for {
        today <- Calendar[F].currentDate
        startDate = weekStart.getOrElse {
          today.minusDays(today.getDayOfWeek.getValue.toLong - 1)
        }
        report <- timeService.getWeeklyReport(user.id, startDate)
        response <- Ok(report)
      } yield response

    case GET -> Root / "productivity" as user =>
      for {
        metrics <- timeService.getProductivityMetrics(user.id)
        response <- Ok(metrics)
      } yield response
  }
}

object TimeTrackingRoutes {
  def apply[F[_]: Async](timeService: TimeTrackingService[F]): TimeTrackingRoutes[F] =
    new TimeTrackingRoutes[F](timeService)
}
