package tm.domain.websocket

import java.time.Instant

import io.circe.generic.JsonCodec

import tm.domain.notifications._

// Base trait for Notification WebSocket Messages
sealed trait NotificationWebSocketMessage {
  def messageType: String
  def timestamp: Instant
}

// Notification WebSocket Messages

@JsonCodec
case class NewNotificationsMessage(
    messageType: String = "new_notifications",
    timestamp: Instant = Instant.now(),
    notifications: List[Notification],
  ) extends NotificationWebSocketMessage

@JsonCodec
case class CountUpdateMessage(
    messageType: String = "count_update",
    timestamp: Instant = Instant.now(),
    unreadCount: Int,
  ) extends NotificationWebSocketMessage

@JsonCodec
case class InitialDataMessage(
    messageType: String = "initial_data",
    timestamp: Instant = Instant.now(),
    unreadCount: Int,
    recentNotifications: List[Notification],
  ) extends NotificationWebSocketMessage

@JsonCodec
case class AllMarkedReadMessage(
    messageType: String = "all_marked_read",
    timestamp: Instant = Instant.now(),
  ) extends NotificationWebSocketMessage

@JsonCodec
case class PingMessage(
    messageType: String = "ping",
    timestamp: Instant = Instant.now(),
  ) extends NotificationWebSocketMessage

@JsonCodec
case class PongMessage(
    messageType: String = "pong",
    timestamp: Instant = Instant.now(),
  ) extends NotificationWebSocketMessage

@JsonCodec
case class NotificationSentMessage(
    messageType: String = "notification_sent",
    timestamp: Instant = Instant.now(),
    notification: Notification,
  ) extends NotificationWebSocketMessage

@JsonCodec
case class ErrorMessage(
    messageType: String = "error",
    timestamp: Instant = Instant.now(),
    error: String,
    details: Option[String] = None,
  ) extends NotificationWebSocketMessage

@JsonCodec
case class AdminNotificationMessage(
    messageType: String = "admin_notification",
    timestamp: Instant = Instant.now(),
    title: String,
    content: String,
    severity: String = "info", // info, warning, error
  ) extends NotificationWebSocketMessage
