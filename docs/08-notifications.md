# Notifications System Backend Implementation

## Implementation Tasks

### 1. Notifications Domain Models
**Priority: 🟡 Medium**
**Fayl**: `endpoints/00-domain/src/main/scala/tm/domain/notifications/`

```scala
case class Notification(
  id: NotificationId,
  userId: UserId,
  title: String,
  content: String,
  notificationType: NotificationType,
  relatedEntityId: Option[String],
  relatedEntityType: Option[String],
  isRead: Boolean,
  priority: NotificationPriority,
  deliveryMethods: Set[DeliveryMethod],
  scheduledAt: Option[Instant],
  sentAt: Option[Instant],
  createdAt: Instant
)

sealed trait NotificationType
object NotificationType {
  case object TaskAssigned extends NotificationType
  case object TaskDue extends NotificationType
  case object ProjectUpdate extends NotificationType
  case object TimeReminder extends NotificationType
  case object SystemAlert extends NotificationType
}

sealed trait DeliveryMethod
object DeliveryMethod {
  case object InApp extends DeliveryMethod
  case object Email extends DeliveryMethod
  case object SMS extends DeliveryMethod
  case object Push extends DeliveryMethod
  case object Telegram extends DeliveryMethod
}

case class NotificationSettings(
  userId: UserId,
  emailNotifications: Boolean,
  pushNotifications: Boolean,
  smsNotifications: Boolean,
  taskReminders: Boolean,
  projectUpdates: Boolean,
  teamUpdates: Boolean,
  quietHours: Option[QuietHours]
)

case class QuietHours(
  startTime: LocalTime,
  endTime: LocalTime,
  timeZone: String,
  weekendsOnly: Boolean
)
```

### 2. Database Schema
```sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    related_entity_id VARCHAR(255),
    related_entity_type VARCHAR(100),
    is_read BOOLEAN DEFAULT false,
    priority VARCHAR(20) DEFAULT 'Normal',
    delivery_methods TEXT[],
    scheduled_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),

    INDEX idx_notifications_user_read(user_id, is_read),
    INDEX idx_notifications_scheduled(scheduled_at) WHERE scheduled_at IS NOT NULL
);

CREATE TABLE notification_settings (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    email_notifications BOOLEAN DEFAULT true,
    push_notifications BOOLEAN DEFAULT true,
    sms_notifications BOOLEAN DEFAULT false,
    task_reminders BOOLEAN DEFAULT true,
    project_updates BOOLEAN DEFAULT true,
    team_updates BOOLEAN DEFAULT true,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    quiet_hours_timezone VARCHAR(50) DEFAULT 'UTC',
    quiet_hours_weekends_only BOOLEAN DEFAULT false,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

### 3. API Routes
```scala
object NotificationRoutes {
  def routes[F[_]: Async](
    notificationService: NotificationService[F],
    authMiddleware: AuthMiddleware[F]
  ): HttpRoutes[F] = {

    val protectedRoutes: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of[AuthedUser, F] {
      case GET -> Root as user =>
        // List notifications

      case POST -> Root / UUIDVar(notificationId) / "read" as user =>
        // Mark as read

      case POST -> Root / "mark-all-read" as user =>
        // Mark all as read

      case GET -> Root / "settings" as user =>
        // Get notification settings

      case PUT -> Root / "settings" as user =>
        // Update notification settings

      case GET -> Root / "unread-count" as user =>
        // Get unread count

      // WebSocket for real-time notifications
      case GET -> Root / "ws" as user =>
        // WebSocket connection
    }

    authMiddleware(protectedRoutes)
  }
}
```

## Estimated Time: 1-2 hafta