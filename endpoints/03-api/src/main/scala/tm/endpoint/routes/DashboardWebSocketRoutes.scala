package tm.endpoint.routes

import scala.concurrent.duration._

import cats.effect.Async
import cats.effect.Concurrent
import cats.effect.Ref
import cats.effect.std.Queue
import cats.implicits._
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Topic
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Close
import org.http4s.websocket.WebSocketFrame.Text

import tm.domain.PersonId
import tm.domain.analytics._
import tm.domain.auth.AuthedUser
import tm.services.AnalyticsService
import tm.support.http4s.utils.Routes

final case class DashboardWebSocketRoutes[F[_]: Async](
    analyticsService: AnalyticsService[F],
    wsBuilder: WebSocketBuilder2[F],
  ) extends Routes[F, AuthedUser]
       with Http4sDsl[F] {
  override val path: String = "/dashboard/ws"
  override val public: HttpRoutes[F] = HttpRoutes.empty[F]
  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {

    // Real-time dashboard updates WebSocket
    case GET -> Root / "live" as user =>
      for {
        topic <- Topic[F, DashboardUpdateMessage]
        queue <- Queue.unbounded[F, WebSocketFrame]

        // Stream that periodically fetches dashboard updates
        updateStream = Stream
          .awakeEvery[F](30.seconds)
          .evalMap(_ => fetchDashboardUpdate(user.id))
          .through(topic.publish)

        // Stream that converts topic messages to WebSocket frames
        outputStream = topic
          .subscribe(10)
          .map(msg => Text(msg.asJson.noSpaces))

        // Input stream handler for WebSocket frames
        inputSink: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
          case Text(data, _) =>
            for {
              _ <- handleWebSocketMessage(data, user.id)
            } yield ()
          case Close(_) =>
            Async[F].unit
          case _ =>
            Async[F].unit
        }

        // Start the update stream in background
        _ <- updateStream.compile.drain.start

        response <- wsBuilder.build(outputStream, inputSink)
      } yield response

    // Team dashboard WebSocket (for managers)
    case GET -> Root / "team" as user =>
      for {
        topic <- Topic[F, TeamUpdateMessage]

        updateStream = Stream
          .awakeEvery[F](60.seconds) // Less frequent updates for team data
          .evalMap(_ => fetchTeamUpdate(user.id))
          .through(topic.publish)

        outputStream = topic
          .subscribe(10)
          .map(msg => Text(msg.asJson.noSpaces))

        inputSink: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
          case Text(data, _) =>
            handleTeamWebSocketMessage(data, user.id)
          case Close(_) =>
            Async[F].unit
          case _ =>
            Async[F].unit
        }

        _ <- updateStream.compile.drain.start
        response <- wsBuilder.build(outputStream, inputSink)
      } yield response

    // Productivity monitoring WebSocket
    case GET -> Root / "productivity" as user =>
      for {
        topic <- Topic[F, ProductivityUpdateMessage]

        updateStream = Stream
          .awakeEvery[F](15.seconds) // More frequent for productivity tracking
          .evalMap(_ => fetchProductivityUpdate(user.id))
          .through(topic.publish)

        outputStream = topic
          .subscribe(5)
          .map(msg => Text(msg.asJson.noSpaces))

        inputSink: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
          case Text(data, _) =>
            handleProductivityMessage(data, user.id)
          case _ =>
            Async[F].unit
        }

        _ <- updateStream.compile.drain.start
        response <- wsBuilder.build(outputStream, inputSink)
      } yield response

    // Goals progress WebSocket
    case GET -> Root / "goals" as user =>
      for {
        topic <- Topic[F, GoalUpdateMessage]

        updateStream = Stream
          .awakeEvery[F](5.minutes) // Goals don't change frequently
          .evalMap(_ => fetchGoalUpdate(user.id))
          .through(topic.publish)

        outputStream = topic
          .subscribe(3)
          .map(msg => Text(msg.asJson.noSpaces))

        inputSink: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
          case Text(data, _) =>
            handleGoalMessage(data, user.id)
          case _ =>
            Async[F].unit
        }

        _ <- updateStream.compile.drain.start
        response <- wsBuilder.build(outputStream, inputSink)
      } yield response

    // Notifications WebSocket
    case GET -> Root / "notifications" as user =>
      for {
        topic <- Topic[F, NotificationUpdateMessage]

        updateStream = Stream
          .awakeEvery[F](10.seconds)
          .evalMap(_ => fetchNotificationUpdate(user.id))
          .through(topic.publish)

        outputStream = topic
          .subscribe(10)
          .map(msg => Text(msg.asJson.noSpaces))

        inputSink: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
          case Text(data, _) =>
            handleNotificationMessage(data, user.id)
          case _ =>
            Async[F].unit
        }

        _ <- updateStream.compile.drain.start
        response <- wsBuilder.build(outputStream, inputSink)
      } yield response
  }

  // Helper methods for fetching updates

  private def fetchDashboardUpdate(userId: PersonId): F[DashboardUpdateMessage] =
    for {
      liveStats <- analyticsService.getLiveWorkStats(userId)
      isWorking <- analyticsService.isUserCurrentlyWorking(userId)
      score <- analyticsService.calculateProductivityScore(userId)
    } yield DashboardUpdateMessage(
      messageType = "dashboard_update",
      timestamp = java.time.Instant.now(),
      data = DashboardUpdateData(
        isWorking = isWorking,
        currentSessionDuration = liveStats.sessionDuration,
        todayProductiveMinutes = liveStats.todayTotal,
        weekTotalHours = liveStats.weekTotal,
        efficiency = liveStats.efficiency,
        productivityScore = score,
      ),
    )

  private def fetchTeamUpdate(managerId: PersonId): F[TeamUpdateMessage] =
    for {
      teamDashboard <- analyticsService.getTeamDashboard(managerId)
    } yield TeamUpdateMessage(
      messageType = "team_update",
      timestamp = java.time.Instant.now(),
      data = TeamUpdateData(
        teamStats = teamDashboard.teamStats,
        activeMembers = teamDashboard.teamStats.activeMembers,
        todayTeamHours = teamDashboard.teamStats.todayHours,
        averageProductivity = teamDashboard.teamStats.productivity,
        alertsCount = teamDashboard.alerts.length,
      ),
    )

  private def fetchProductivityUpdate(userId: PersonId): F[ProductivityUpdateMessage] =
    for {
      liveStats <- analyticsService.getLiveWorkStats(userId)
      insights <- analyticsService.getProductivityInsights(userId)
    } yield ProductivityUpdateMessage(
      messageType = "productivity_update",
      timestamp = java.time.Instant.now(),
      data = ProductivityUpdateData(
        currentEfficiency = liveStats.efficiency,
        sessionDuration = liveStats.sessionDuration,
        todayProgress = (liveStats.todayTotal / 480.0) * 100, // 8 hours = 480 minutes
        newInsightsCount = insights.length,
        isProductiveSession = liveStats.sessionDuration > 25 && liveStats.efficiency > 70,
      ),
    )

  private def fetchGoalUpdate(userId: PersonId): F[GoalUpdateMessage] =
    for {
      goalProgress <- analyticsService.getGoalProgress(userId)
    } yield GoalUpdateMessage(
      messageType = "goal_update",
      timestamp = java.time.Instant.now(),
      data = GoalUpdateData(
        dailyProgress = goalProgress.dailyProgress,
        weeklyProgress = goalProgress.weeklyProgress,
        streakProgress = goalProgress.streakProgress,
        productivityProgress = goalProgress.productivityProgress,
      ),
    )

  private def fetchNotificationUpdate(userId: PersonId): F[NotificationUpdateMessage] =
    for {
      notifications <- analyticsService.getDashboardNotifications(userId)
      unreadCount = notifications.count(!_.isRead)
    } yield NotificationUpdateMessage(
      messageType = "notification_update",
      timestamp = java.time.Instant.now(),
      data = NotificationUpdateData(
        unreadCount = unreadCount,
        latestNotifications = notifications.take(5),
        hasHighPriority = notifications.exists(_.priority == NotificationPriority.High),
      ),
    )

  // Message handlers

  private def handleWebSocketMessage(data: String, userId: PersonId): F[Unit] =
    // Parse incoming WebSocket messages and handle them
    // For example: ping/pong, user commands, etc.
    if (data.contains("ping"))
      Async[F].unit // Handle ping message
    else
      Async[F].unit // Handle other messages

  private def handleTeamWebSocketMessage(data: String, managerId: PersonId): F[Unit] =
    // Handle team-specific WebSocket messages
    Async[F].unit

  private def handleProductivityMessage(data: String, userId: PersonId): F[Unit] =
    // Handle productivity-specific messages
    Async[F].unit

  private def handleGoalMessage(data: String, userId: PersonId): F[Unit] =
    // Handle goal-related messages
    Async[F].unit

  private def handleNotificationMessage(data: String, userId: PersonId): F[Unit] =
    // Handle notification-related messages (e.g., mark as read)
    if (data.contains("mark_read"))
      // Extract notification ID and mark as read
      Async[F].unit
    else
      Async[F].unit
}

// WebSocket Message Types

sealed trait WebSocketMessage {
  def messageType: String
  def timestamp: java.time.Instant
}

case class DashboardUpdateMessage(
    messageType: String,
    timestamp: java.time.Instant,
    data: DashboardUpdateData,
  ) extends WebSocketMessage

case class DashboardUpdateData(
    isWorking: Boolean,
    currentSessionDuration: Int,
    todayProductiveMinutes: Int,
    weekTotalHours: Double,
    efficiency: Double,
    productivityScore: Double,
  )

case class TeamUpdateMessage(
    messageType: String,
    timestamp: java.time.Instant,
    data: TeamUpdateData,
  ) extends WebSocketMessage

case class TeamUpdateData(
    teamStats: TeamStats,
    activeMembers: Int,
    todayTeamHours: Double,
    averageProductivity: Double,
    alertsCount: Int,
  )

case class ProductivityUpdateMessage(
    messageType: String,
    timestamp: java.time.Instant,
    data: ProductivityUpdateData,
  ) extends WebSocketMessage

case class ProductivityUpdateData(
    currentEfficiency: Double,
    sessionDuration: Int,
    todayProgress: Double,
    newInsightsCount: Int,
    isProductiveSession: Boolean,
  )

case class GoalUpdateMessage(
    messageType: String,
    timestamp: java.time.Instant,
    data: GoalUpdateData,
  ) extends WebSocketMessage

case class GoalUpdateData(
    dailyProgress: Double,
    weeklyProgress: Double,
    streakProgress: Int,
    productivityProgress: Double,
  )

case class NotificationUpdateMessage(
    messageType: String,
    timestamp: java.time.Instant,
    data: NotificationUpdateData,
  ) extends WebSocketMessage

case class NotificationUpdateData(
    unreadCount: Int,
    latestNotifications: List[DashboardNotification],
    hasHighPriority: Boolean,
  )

// JSON Codecs for WebSocket messages
object DashboardUpdateMessage {
  implicit val encoder: Encoder[DashboardUpdateMessage] = deriveEncoder
  implicit val decoder: Decoder[DashboardUpdateMessage] = deriveDecoder
}

object DashboardUpdateData {
  implicit val encoder: Encoder[DashboardUpdateData] = deriveEncoder
  implicit val decoder: Decoder[DashboardUpdateData] = deriveDecoder
}

object TeamUpdateMessage {
  implicit val encoder: Encoder[TeamUpdateMessage] = deriveEncoder
  implicit val decoder: Decoder[TeamUpdateMessage] = deriveDecoder
}

object TeamUpdateData {
  implicit val encoder: Encoder[TeamUpdateData] = deriveEncoder
  implicit val decoder: Decoder[TeamUpdateData] = deriveDecoder
}

object ProductivityUpdateMessage {
  implicit val encoder: Encoder[ProductivityUpdateMessage] = deriveEncoder
  implicit val decoder: Decoder[ProductivityUpdateMessage] = deriveDecoder
}

object ProductivityUpdateData {
  implicit val encoder: Encoder[ProductivityUpdateData] = deriveEncoder
  implicit val decoder: Decoder[ProductivityUpdateData] = deriveDecoder
}

object GoalUpdateMessage {
  implicit val encoder: Encoder[GoalUpdateMessage] = deriveEncoder
  implicit val decoder: Decoder[GoalUpdateMessage] = deriveDecoder
}

object GoalUpdateData {
  implicit val encoder: Encoder[GoalUpdateData] = deriveEncoder
  implicit val decoder: Decoder[GoalUpdateData] = deriveDecoder
}

object NotificationUpdateMessage {
  implicit val encoder: Encoder[NotificationUpdateMessage] = deriveEncoder
  implicit val decoder: Decoder[NotificationUpdateMessage] = deriveDecoder
}

object NotificationUpdateData {
  implicit val encoder: Encoder[NotificationUpdateData] = deriveEncoder
  implicit val decoder: Decoder[NotificationUpdateData] = deriveDecoder
}

object DashboardWebSocketRoutes {
  def apply[F[_]: Async](
      analyticsService: AnalyticsService[F],
      wsBuilder: WebSocketBuilder2[F],
    ): DashboardWebSocketRoutes[F] =
    new DashboardWebSocketRoutes[F](analyticsService, wsBuilder)
}
