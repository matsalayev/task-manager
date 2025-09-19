package tm.endpoint.routes

import scala.concurrent.duration._

import cats.effect.Async
import cats.effect.std.Queue
import cats.implicits._
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Topic
import io.circe.Encoder
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Close
import org.http4s.websocket.WebSocketFrame.Ping
import org.http4s.websocket.WebSocketFrame.Text

import tm.domain.PersonId
import tm.domain.auth.AuthedUser
import tm.domain.notifications._
import tm.domain.websocket._
import tm.services.NotificationService
import tm.support.http4s.utils.Routes

final case class NotificationWebSocketRoutes[F[_]: Async](
    notificationService: NotificationService[F],
    wsBuilder: WebSocketBuilder2[F],
  ) extends Routes[F, AuthedUser]
       with Http4sDsl[F] {

  // Implicit encoder for WebSocket messages
  implicit val notificationWSMessageEncoder
      : Encoder[tm.domain.websocket.NotificationWebSocketMessage] =
    Encoder.instance {
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
  override val path: String = "/notifications/ws"
  override val public: HttpRoutes[F] = HttpRoutes.empty[F]
  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {

    // Real-time notifications WebSocket
    case GET -> Root as user =>
      for {
        // Create topic for broadcasting notifications
        topic <- Topic[F, tm.domain.websocket.NotificationWebSocketMessage]

        // Create queue for managing user-specific messages
        userQueue <- Queue.unbounded[F, tm.domain.websocket.NotificationWebSocketMessage]

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
        _ <- Async[F].start(notificationChecker)

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
            getUnreadCountUpdate(user.id).flatMap(topic.publish1).void
          case Close(_) =>
            Async[F].unit
          case _ =>
            Async[F].unit
        }

        _ <- Async[F].start(countChecker.compile.drain)
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

        _ <- Async[F].start(adminChecker.compile.drain)
        response <- wsBuilder.build(outputStream, inputSink)
      } yield response
  }

  // Helper methods for WebSocket functionality

  private def checkForNewNotifications(
      userId: PersonId
    ): F[Option[tm.domain.websocket.NotificationWebSocketMessage]] =
    for {
      unreadNotifications <- notificationService.getUnreadNotifications(userId)
      recentNotifications = unreadNotifications.take(5)
      unreadCount <- notificationService.getUnreadCount(userId)
    } yield
      if (recentNotifications.nonEmpty)
        Some(
          NewNotificationsMessage(notifications = recentNotifications)
        )
      else
        None

  private def sendInitialData(
      userId: PersonId,
      queue: Queue[F, tm.domain.websocket.NotificationWebSocketMessage],
    ): F[Unit] =
    for {
      unreadCount <- notificationService.getUnreadCount(userId)
      recentNotifications <- notificationService.getUnreadNotifications(userId).map(_.take(10))
      stats <- notificationService.getNotificationStats(userId)

      initialMessage = InitialDataMessage(
        unreadCount = unreadCount.toInt,
        recentNotifications = recentNotifications,
      )

      _ <- queue.offer(initialMessage)
    } yield ()

  private def handleIncomingMessage(
      data: String,
      userId: PersonId,
      queue: Queue[F, tm.domain.websocket.NotificationWebSocketMessage],
    ): F[Unit] = {
    val result = for {
      json <- io.circe.parser.parse(data)
      messageType <- json.hcursor.get[String]("type")
    } yield messageType

    result match {
      case Right("mark_read") =>
        for {
          parsed <- Async[F].fromEither(io.circe.parser.parse(data))
          notificationId <- Async[F].fromEither(parsed.hcursor.get[String]("notificationId"))
          uuid <- Async[F].fromTry(scala.util.Try(java.util.UUID.fromString(notificationId)))
          _ <- notificationService.markAsRead(NotificationId(uuid), userId)
          unreadCount <- notificationService.getUnreadCount(userId)
          _ <- queue.offer(CountUpdateMessage(unreadCount = unreadCount.toInt))
        } yield ()

      case Right("mark_all_read") =>
        for {
          _ <- notificationService.markAllAsRead(userId)
          _ <- queue.offer(CountUpdateMessage(unreadCount = 0))
          _ <- queue.offer(AllMarkedReadMessage())
        } yield ()

      case Right("get_unread_count") =>
        for {
          count <- notificationService.getUnreadCount(userId)
          _ <- queue.offer(CountUpdateMessage(unreadCount = count.toInt))
        } yield ()

      case Right("get_recent") =>
        for {
          recent <- notificationService.getUnreadNotifications(userId).map(_.take(10))
          count <- notificationService.getUnreadCount(userId)
          _ <- queue.offer(NewNotificationsMessage(notifications = recent))
        } yield ()

      case Right("ping") =>
        queue.offer(PongMessage())

      case _ =>
        // Unknown message type, ignore
        Async[F].unit
    }
  }

  private def getUnreadCountUpdate(userId: PersonId): F[CountUpdateMessage] =
    for {
      count <- notificationService.getUnreadCount(userId)
    } yield CountUpdateMessage(unreadCount = count.toInt)

  private def getAdminNotificationData(): F[AdminNotificationMessage] =
    for {
      // TODO: Implement admin-specific notification data
      // This would include system-wide notification statistics
      timestamp <- Async[F].delay(java.time.Instant.now())
    } yield AdminNotificationMessage(
      title = "System Health",
      content = "System is healthy",
      severity = "info",
    )

  private def handleAdminMessage(data: String, userId: PersonId): F[Unit] =
    // TODO: Implement admin message handling
    // This could include triggering notification processing, cleanup, etc.
    Async[F].unit
}

object NotificationWebSocketRoutes {
  def apply[F[_]: Async](
      notificationService: NotificationService[F],
      wsBuilder: WebSocketBuilder2[F],
    ): NotificationWebSocketRoutes[F] =
    new NotificationWebSocketRoutes[F](notificationService, wsBuilder)
}
