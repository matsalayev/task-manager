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
import org.http4s.websocket.WebSocketFrame.Ping
import org.http4s.websocket.WebSocketFrame.Pong
import org.http4s.websocket.WebSocketFrame.Text

import tm.domain.PersonId
import tm.domain.auth.AuthedUser
import tm.domain.notifications._
import tm.services.NotificationService
import tm.support.http4s.utils.Routes

final case class NotificationWebSocketRoutes[F[_]: Async](
    notificationService: NotificationService[F],
    wsBuilder: WebSocketBuilder2[F],
  ) extends Routes[F, AuthedUser]
       with Http4sDsl[F] {
  override val path: String = "/notifications/ws"
  override val public: HttpRoutes[F] = HttpRoutes.empty[F]
  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {

    // Real-time notifications WebSocket
    case GET -> Root as user =>
      for {
        // Create topic for broadcasting notifications
        topic <- Topic[F, NotificationWebSocketMessage]

        // Create queue for managing user-specific messages
        userQueue <- Queue.unbounded[F, NotificationWebSocketMessage]

        // Periodic check for new notifications
        checkInterval = 5.seconds
        notificationChecker = Stream
          .awakeEvery[F](checkInterval)
          .evalMap(_ => checkForNewNotifications(user.id))
          .unNone
          .evalTap(userQueue.offer)
          .compile
          .drain

        // Stream of outgoing messages
        outputStream = Stream
          .fromQueueUnterminated(userQueue)
          .merge(Stream.awakeEvery[F](30.seconds).as(PingMessage()))
          .map(msg => Text(msg.asJson.noSpaces))

        // Handle incoming WebSocket frames
        inputSink: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
          case Text(data, _) =>
            handleIncomingMessage(data, user.id, userQueue)
          case Ping(data) =>
            // Handle ping with pong
            userQueue.offer(PongMessage(data.toArray.map(_.toChar).mkString))
          case Close(_) =>
            Async[F].unit
          case _ =>
            Async[F].unit
        }

        // Start the notification checker in background
        _ <- notificationChecker.start

        // Send initial data
        _ <- sendInitialData(user.id, userQueue)

        response <- wsBuilder.build(outputStream, inputSink)
      } yield response

    // Notification count WebSocket (lightweight)
    case GET -> Root / "count" as user =>
      for {
        topic <- Topic[F, CountUpdateMessage]

        countChecker = Stream
          .awakeEvery[F](10.seconds)
          .evalMap(_ => getUnreadCountUpdate(user.id))
          .through(topic.publish)

        outputStream = topic
          .subscribe(5)
          .map(msg => Text(msg.asJson.noSpaces))

        inputSink: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
          case Text("refresh", _) =>
            getUnreadCountUpdate(user.id).flatMap(topic.publish1)
          case Close(_) =>
            Async[F].unit
          case _ =>
            Async[F].unit
        }

        _ <- countChecker.compile.drain.start
        response <- wsBuilder.build(outputStream, inputSink)
      } yield response

    // Notification admin WebSocket (for monitoring)
    case GET -> Root / "admin" as user =>
      // TODO: Add proper admin authorization check
      for {
        topic <- Topic[F, AdminNotificationMessage]

        adminChecker = Stream
          .awakeEvery[F](30.seconds)
          .evalMap(_ => getAdminNotificationData())
          .through(topic.publish)

        outputStream = topic
          .subscribe(10)
          .map(msg => Text(msg.asJson.noSpaces))

        inputSink: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
          case Text(data, _) =>
            handleAdminMessage(data, user.id)
          case Close(_) =>
            Async[F].unit
          case _ =>
            Async[F].unit
        }

        _ <- adminChecker.compile.drain.start
        response <- wsBuilder.build(outputStream, inputSink)
      } yield response
  }

  // Helper methods for WebSocket functionality

  private def checkForNewNotifications(userId: PersonId): F[Option[NotificationWebSocketMessage]] =
    for {
      unreadNotifications <- notificationService.getUnreadNotifications(userId)
      recentNotifications = unreadNotifications.take(5)
      unreadCount <- notificationService.getUnreadCount(userId)
    } yield
      if (recentNotifications.nonEmpty)
        Some(
          NewNotificationsMessage(
            notifications = recentNotifications,
            unreadCount = unreadCount,
            timestamp = java.time.Instant.now(),
          )
        )
      else
        None

  private def sendInitialData(
      userId: PersonId,
      queue: Queue[F, NotificationWebSocketMessage],
    ): F[Unit] =
    for {
      unreadCount <- notificationService.getUnreadCount(userId)
      recentNotifications <- notificationService.getUnreadNotifications(userId).map(_.take(10))
      stats <- notificationService.getNotificationStats(userId)

      initialMessage = InitialDataMessage(
        unreadCount = unreadCount,
        recentNotifications = recentNotifications,
        stats = stats,
        timestamp = java.time.Instant.now(),
      )

      _ <- queue.offer(initialMessage)
    } yield ()

  private def handleIncomingMessage(
      data: String,
      userId: PersonId,
      queue: Queue[F, NotificationWebSocketMessage],
    ): F[Unit] = {
    val result = for {
      json <- io.circe.parser.parse(data)
      messageType <- json.hcursor.get[String]("type")
    } yield messageType

    result match {
      case Right("mark_read") =>
        for {
          json <- io.circe.parser.parse(data).pure[F]
          notificationId <- json.hcursor.get[String]("notificationId").pure[F]
          uuid <- Async[F].fromTry(scala.util.Try(java.util.UUID.fromString(notificationId)))
          _ <- notificationService.markAsRead(NotificationId(uuid), userId)
          unreadCount <- notificationService.getUnreadCount(userId)
          _ <- queue.offer(CountUpdateMessage(unreadCount, java.time.Instant.now()))
        } yield ()

      case Right("mark_all_read") =>
        for {
          _ <- notificationService.markAllAsRead(userId)
          _ <- queue.offer(CountUpdateMessage(0, java.time.Instant.now()))
          _ <- queue.offer(AllMarkedReadMessage(java.time.Instant.now()))
        } yield ()

      case Right("get_unread_count") =>
        for {
          count <- notificationService.getUnreadCount(userId)
          _ <- queue.offer(CountUpdateMessage(count, java.time.Instant.now()))
        } yield ()

      case Right("get_recent") =>
        for {
          recent <- notificationService.getUnreadNotifications(userId).map(_.take(10))
          count <- notificationService.getUnreadCount(userId)
          _ <- queue.offer(NewNotificationsMessage(recent, count, java.time.Instant.now()))
        } yield ()

      case Right("ping") =>
        queue.offer(PongMessage("pong"))

      case _ =>
        // Unknown message type, ignore
        Async[F].unit
    }
  }

  private def getUnreadCountUpdate(userId: PersonId): F[CountUpdateMessage] =
    for {
      count <- notificationService.getUnreadCount(userId)
    } yield CountUpdateMessage(count, java.time.Instant.now())

  private def getAdminNotificationData(): F[AdminNotificationMessage] =
    for {
      // TODO: Implement admin-specific notification data
      // This would include system-wide notification statistics
      timestamp <- Async[F].delay(java.time.Instant.now())
    } yield AdminNotificationMessage(
      totalNotifications = 0, // Placeholder
      totalUnread = 0, // Placeholder
      recentActivity = List.empty, // Placeholder
      systemHealth = "healthy",
      timestamp = timestamp,
    )

  private def handleAdminMessage(data: String, userId: PersonId): F[Unit] =
    // TODO: Implement admin message handling
    // This could include triggering notification processing, cleanup, etc.
    Async[F].unit
}

// WebSocket Message Types
sealed trait NotificationWebSocketMessage {
  def messageType: String
  def timestamp: java.time.Instant
}

case class NewNotificationsMessage(
    notifications: List[Notification],
    unreadCount: Long,
    timestamp: java.time.Instant,
  ) extends NotificationWebSocketMessage {
  val messageType = "new_notifications"
}

case class CountUpdateMessage(
    unreadCount: Long,
    timestamp: java.time.Instant,
  ) extends NotificationWebSocketMessage {
  val messageType = "count_update"
}

case class InitialDataMessage(
    unreadCount: Long,
    recentNotifications: List[Notification],
    stats: NotificationStats,
    timestamp: java.time.Instant,
  ) extends NotificationWebSocketMessage {
  val messageType = "initial_data"
}

case class AllMarkedReadMessage(
    timestamp: java.time.Instant
  ) extends NotificationWebSocketMessage {
  val messageType = "all_marked_read"
}

case class PingMessage(
    timestamp: java.time.Instant = java.time.Instant.now()
  ) extends NotificationWebSocketMessage {
  val messageType = "ping"
}

case class PongMessage(
    data: String,
    timestamp: java.time.Instant = java.time.Instant.now(),
  ) extends NotificationWebSocketMessage {
  val messageType = "pong"
}

case class NotificationSentMessage(
    notification: Notification,
    timestamp: java.time.Instant,
  ) extends NotificationWebSocketMessage {
  val messageType = "notification_sent"
}

case class ErrorMessage(
    error: String,
    code: String,
    timestamp: java.time.Instant,
  ) extends NotificationWebSocketMessage {
  val messageType = "error"
}

case class AdminNotificationMessage(
    totalNotifications: Long,
    totalUnread: Long,
    recentActivity: List[String],
    systemHealth: String,
    timestamp: java.time.Instant,
  ) extends NotificationWebSocketMessage {
  val messageType = "admin_data"
}

// JSON Codecs for WebSocket messages
object NewNotificationsMessage {
  implicit val encoder: Encoder[NewNotificationsMessage] = deriveEncoder
  implicit val decoder: Decoder[NewNotificationsMessage] = deriveDecoder
}

object CountUpdateMessage {
  implicit val encoder: Encoder[CountUpdateMessage] = deriveEncoder
  implicit val decoder: Decoder[CountUpdateMessage] = deriveDecoder
}

object InitialDataMessage {
  implicit val encoder: Encoder[InitialDataMessage] = deriveEncoder
  implicit val decoder: Decoder[InitialDataMessage] = deriveDecoder
}

object AllMarkedReadMessage {
  implicit val encoder: Encoder[AllMarkedReadMessage] = deriveEncoder
  implicit val decoder: Decoder[AllMarkedReadMessage] = deriveDecoder
}

object PingMessage {
  implicit val encoder: Encoder[PingMessage] = deriveEncoder
  implicit val decoder: Decoder[PingMessage] = deriveDecoder
}

object PongMessage {
  implicit val encoder: Encoder[PongMessage] = deriveEncoder
  implicit val decoder: Decoder[PongMessage] = deriveDecoder
}

object NotificationSentMessage {
  implicit val encoder: Encoder[NotificationSentMessage] = deriveEncoder
  implicit val decoder: Decoder[NotificationSentMessage] = deriveDecoder
}

object ErrorMessage {
  implicit val encoder: Encoder[ErrorMessage] = deriveEncoder
  implicit val decoder: Decoder[ErrorMessage] = deriveDecoder
}

object AdminNotificationMessage {
  implicit val encoder: Encoder[AdminNotificationMessage] = deriveEncoder
  implicit val decoder: Decoder[AdminNotificationMessage] = deriveDecoder
}

// Umbrella encoder/decoder for all message types
object NotificationWebSocketMessage {
  implicit val encoder: Encoder[NotificationWebSocketMessage] = Encoder.instance {
    case msg: NewNotificationsMessage => msg.asJson
    case msg: CountUpdateMessage => msg.asJson
    case msg: InitialDataMessage => msg.asJson
    case msg: AllMarkedReadMessage => msg.asJson
    case msg: PingMessage => msg.asJson
    case msg: PongMessage => msg.asJson
    case msg: NotificationSentMessage => msg.asJson
    case msg: ErrorMessage => msg.asJson
    case msg: AdminNotificationMessage => msg.asJson
  }

  implicit val decoder: Decoder[NotificationWebSocketMessage] = Decoder.instance { cursor =>
    cursor.get[String]("messageType").flatMap {
      case "new_notifications" => cursor.as[NewNotificationsMessage]
      case "count_update" => cursor.as[CountUpdateMessage]
      case "initial_data" => cursor.as[InitialDataMessage]
      case "all_marked_read" => cursor.as[AllMarkedReadMessage]
      case "ping" => cursor.as[PingMessage]
      case "pong" => cursor.as[PongMessage]
      case "notification_sent" => cursor.as[NotificationSentMessage]
      case "error" => cursor.as[ErrorMessage]
      case "admin_data" => cursor.as[AdminNotificationMessage]
      case other => Left(io.circe.DecodingFailure(s"Unknown message type: $other", cursor.history))
    }
  }
}

object NotificationWebSocketRoutes {
  def apply[F[_]: Async](
      notificationService: NotificationService[F],
      wsBuilder: WebSocketBuilder2[F],
    ): NotificationWebSocketRoutes[F] =
    new NotificationWebSocketRoutes[F](notificationService, wsBuilder)
}
