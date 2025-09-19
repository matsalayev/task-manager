package tm.domain.websocket

import java.time.Instant

import io.circe.generic.JsonCodec

import tm.domain.analytics._

// Base WebSocket Message Trait
sealed trait WebSocketMessage {
  def messageType: String
  def timestamp: Instant
}

// Dashboard Update Messages
@JsonCodec
case class DashboardUpdateMessage(
    messageType: String,
    timestamp: Instant,
    data: DashboardUpdateData,
  ) extends WebSocketMessage

@JsonCodec
case class DashboardUpdateData(
    isWorking: Boolean,
    currentSessionDuration: Int,
    todayProductiveMinutes: Int,
    weekTotalHours: Double,
    efficiency: Double,
    productivityScore: Double,
  )

// Team Update Messages
@JsonCodec
case class TeamUpdateMessage(
    messageType: String,
    timestamp: Instant,
    data: TeamUpdateData,
  ) extends WebSocketMessage

@JsonCodec
case class TeamUpdateData(
    teamStats: TeamStats,
    activeMembers: Int,
    todayTeamHours: Double,
    averageProductivity: Double,
    alertsCount: Int,
  )

// Productivity Update Messages
@JsonCodec
case class ProductivityUpdateMessage(
    messageType: String,
    timestamp: Instant,
    data: ProductivityUpdateData,
  ) extends WebSocketMessage

@JsonCodec
case class ProductivityUpdateData(
    currentEfficiency: Double,
    sessionDuration: Int,
    todayProgress: Double,
    newInsightsCount: Int,
    isProductiveSession: Boolean,
  )

// Goal Update Messages
@JsonCodec
case class GoalUpdateMessage(
    messageType: String,
    timestamp: Instant,
    data: GoalUpdateData,
  ) extends WebSocketMessage

@JsonCodec
case class GoalUpdateData(
    dailyProgress: Double,
    weeklyProgress: Double,
    streakProgress: Int,
    productivityProgress: Double,
  )

// Notification Update Messages
@JsonCodec
case class NotificationUpdateMessage(
    messageType: String,
    timestamp: Instant,
    data: NotificationUpdateData,
  ) extends WebSocketMessage

@JsonCodec
case class NotificationUpdateData(
    unreadCount: Int,
    latestNotifications: List[DashboardNotification],
    hasHighPriority: Boolean,
  )
