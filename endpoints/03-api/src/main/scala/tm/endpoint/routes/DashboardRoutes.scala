package tm.endpoint.routes

import cats.effect.Async
import cats.implicits._
import io.circe.Json
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.headers.`Content-Type`

import tm.domain.auth.AuthedUser
import tm.effects.Calendar
import tm.endpoint.routes.utils.QueryParam._
import tm.services.AnalyticsService
import tm.services.AnalyticsServiceCodecs._
import tm.services.UserGoalsUpdate
import tm.support.http4s.utils.Routes

final case class DashboardRoutes[F[_]: Async](
    analyticsService: AnalyticsService[F]
  ) extends Routes[F, AuthedUser] {
  override val path: String = "/dashboard"
  override val public: HttpRoutes[F] = HttpRoutes.empty[F]
  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {

    // Personal Dashboard endpoints
    case GET -> Root / "personal" as user =>
      for {
        dashboard <- analyticsService.getPersonalDashboard(user.id)
        response <- Ok(dashboard)
      } yield response

    case GET -> Root / "data" as user =>
      for {
        dashboardData <- analyticsService.getDashboardData(user.id)
        response <- Ok(dashboardData)
      } yield response

    case GET -> Root / "live" as user =>
      for {
        liveStats <- analyticsService.getLiveWorkStats(user.id)
        response <- Ok(liveStats)
      } yield response

    case GET -> Root / "working" as user =>
      for {
        isWorking <- analyticsService.isUserCurrentlyWorking(user.id)
        response <- Ok(Json.obj("isWorking" -> Json.fromBoolean(isWorking)))
      } yield response

    // Team Dashboard endpoints (for managers)
    case GET -> Root / "team" as user =>
      for {
        teamDashboard <- analyticsService.getTeamDashboard(user.id)
        response <- Ok(teamDashboard)
      } yield response

    // Analytics and Reports endpoints
    case req @ POST -> Root / "productivity" / "report" as user =>
      for {
        dateRange <- req.req.as[tm.services.DateRange]
        report <- analyticsService.getProductivityReport(user.id, dateRange)
        response <- Ok(report)
      } yield response

    case GET -> Root / "productivity" / "score" as user =>
      for {
        score <- analyticsService.calculateProductivityScore(user.id)
        response <- Ok(Json.obj("productivityScore" -> Json.fromDoubleOrNull(score)))
      } yield response

    // Insights and Recommendations
    case GET -> Root / "insights" as user =>
      for {
        insights <- analyticsService.getProductivityInsights(user.id)
        response <- Ok(insights)
      } yield response

    case POST -> Root / "insights" / "generate" as user =>
      for {
        insights <- analyticsService.generatePersonalizedInsights(user.id)
        response <- Ok(insights)
      } yield response

    // Goals Management
    case GET -> Root / "goals" as user =>
      for {
        progress <- analyticsService.getGoalProgress(user.id)
        response <- Ok(progress)
      } yield response

    case req @ PUT -> Root / "goals" as user =>
      for {
        goalsUpdate <- req.req.as[UserGoalsUpdate]
        goals <- analyticsService.setGoals(user.id, goalsUpdate)
        response <- Ok(goals)
      } yield response

    case GET -> Root / "goals" / "progress" as user =>
      for {
        progress <- analyticsService.getGoalProgress(user.id)
        response <- Ok(progress)
      } yield response

    // Notifications Management
    case GET -> Root / "notifications" as user =>
      for {
        notifications <- analyticsService.getDashboardNotifications(user.id)
        response <- Ok(notifications)
      } yield response

    case POST -> Root / "notifications" / "generate" as user =>
      for {
        notifications <- analyticsService.generateDashboardNotifications(user.id)
        response <- Ok(notifications)
      } yield response

    case POST -> Root / "notifications" / notificationId / "read" as user =>
      for {
        _ <- analyticsService.markNotificationAsRead(notificationId, user.id)
        response <- Ok(Json.obj("success" -> Json.fromBoolean(true)))
      } yield response

    // Charts and Visualization Data endpoints
    case GET -> Root / "charts" / "today" as user =>
      for {
        dashboardData <- analyticsService.getDashboardData(user.id)
        response <- Ok(Json.obj("data" -> Json.fromString("today-stats")))
      } yield response

    case GET -> Root / "charts" / "week" as user =>
      for {
        dashboardData <- analyticsService.getDashboardData(user.id)
        weekStats = dashboardData.weekStats
        response <- Ok(Json.obj("data" -> Json.fromString("week-stats")))
      } yield response

    case GET -> Root / "charts" / "productivity-trend" :? OptionalDays(days) as user =>
      for {
        endDate <- Calendar[F].currentDate
        startDate = endDate.minusDays(days.getOrElse(30).toLong)
        dateRange = tm.services.DateRange(startDate, endDate)
        report <- analyticsService.getProductivityReport(user.id, dateRange)
        response <- Ok(Json.obj("data" -> Json.fromString("productivity-trend")))
      } yield response

    // Time Series Data for charts
    case GET -> Root / "charts" / "time-series" / "daily" :? OptionalDays(days) as _ =>
      for {
        endDate <- Calendar[F].currentDate
        startDate = endDate.minusDays(days.getOrElse(7).toLong)
        // TODO: Implement actual time series data retrieval
        response <- Ok(Json.obj("data" -> Json.fromString("time-series-daily")))
      } yield response

    case GET -> Root / "charts" / "time-series" / "hourly" :? OptionalDate(date) as _ =>
      for {
        targetDate <- date.fold(Calendar[F].currentDate)(_.pure[F])
        // TODO: Implement hourly productivity pattern data
        response <- Ok(Json.obj("data" -> Json.fromString("time-series-hourly")))
      } yield response

    // Statistics and Metrics endpoints
    case GET -> Root / "stats" / "summary" as user =>
      for {
        dashboardData <- analyticsService.getDashboardData(user.id)
        response <- Ok(Json.obj("data" -> Json.fromString("stats-summary")))
      } yield response

    case GET -> Root / "stats" / "comparison" :? OptionalPeriod(_) as _ =>
      for {
        // TODO: Implement period comparison logic
        response <- Ok(Json.obj("data" -> Json.fromString("stats-comparison")))
      } yield response

    // Administrative endpoints
    case POST -> Root / "admin" / "refresh" as _ =>
      // TODO: Add proper admin authorization check
      for {
        _ <- analyticsService.refreshAnalyticsData()
        response <- Ok(
          Json.obj("message" -> Json.fromString("Analytics data refreshed successfully"))
        )
      } yield response

    // Export endpoints
    case GET -> Root / "export" / "dashboard" / "json" :? OptionalDate(_) as user =>
      for {
        dashboardData <- analyticsService.getDashboardData(user.id)
        response <- Ok(dashboardData)
          .map(_.withContentType(`Content-Type`(MediaType.application.json)))
      } yield response

    case GET -> Root / "export" / "productivity" / "csv" :? OptionalDays(days) as user =>
      for {
        endDate <- Calendar[F].currentDate
        startDate = endDate.minusDays(days.getOrElse(30).toLong)
        dateRange = tm.services.DateRange(startDate, endDate)
        report <- analyticsService.getProductivityReport(user.id, dateRange)
        // TODO: Convert to CSV format
        csvData = "Date,Productive Hours,Efficiency\n" // Stub CSV header
        response <- Ok(csvData)
          .map(_.withContentType(`Content-Type`(MediaType.text.csv)))
      } yield response

    // Health check for analytics system
    case GET -> Root / "health" as user =>
      for {
        isWorking <- analyticsService.isUserCurrentlyWorking(user.id)
        response <- Ok(
          Json.obj(
            "status" -> Json.fromString("healthy"),
            "userActive" -> Json.fromBoolean(isWorking),
          )
        )
      } yield response
  }
}

object DashboardRoutes {
  def apply[F[_]: Async](analyticsService: AnalyticsService[F]): DashboardRoutes[F] =
    new DashboardRoutes[F](analyticsService)
}
