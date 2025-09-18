package tm.domain.notifications

import java.time.LocalTime
import java.time.ZonedDateTime

import enumeratum._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto._
import io.circe.refined._

import tm.domain.PersonId
import tm.syntax.circe._

case class Notification(
    id: NotificationId,
    userId: PersonId,
    title: NonEmptyString,
    content: String,
    notificationType: NotificationType,
    relatedEntityId: Option[String],
    relatedEntityType: Option[EntityType],
    isRead: Boolean,
    priority: NotificationPriority,
    deliveryMethods: Set[DeliveryMethod],
    metadata: Map[String, String],
    scheduledAt: Option[ZonedDateTime],
    sentAt: Option[ZonedDateTime],
    readAt: Option[ZonedDateTime],
    expiresAt: Option[ZonedDateTime],
    actionUrl: Option[String],
    actionLabel: Option[String],
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
  )

sealed trait NotificationType extends EnumEntry
object NotificationType extends Enum[NotificationType] with CirceEnum[NotificationType] {
  case object TaskAssigned extends NotificationType
  case object TaskDue extends NotificationType
  case object TaskCompleted extends NotificationType
  case object TaskOverdue extends NotificationType
  case object ProjectUpdate extends NotificationType
  case object ProjectDeadline extends NotificationType
  case object TimeReminder extends NotificationType
  case object BreakReminder extends NotificationType
  case object DailyGoalReached extends NotificationType
  case object WeeklyGoalReached extends NotificationType
  case object SystemAlert extends NotificationType
  case object TeamUpdate extends NotificationType
  case object MentionInComment extends NotificationType
  case object WorkSessionStarted extends NotificationType
  case object ProductivityInsight extends NotificationType

  val values = findValues
}

sealed trait EntityType extends EnumEntry
object EntityType extends Enum[EntityType] with CirceEnum[EntityType] {
  case object Task extends EntityType
  case object Project extends EntityType
  case object User extends EntityType
  case object Team extends EntityType
  case object Comment extends EntityType
  case object TimeEntry extends EntityType
  case object Goal extends EntityType

  val values = findValues
}

sealed trait NotificationPriority extends EnumEntry
object NotificationPriority
    extends Enum[NotificationPriority]
       with CirceEnum[NotificationPriority] {
  case object Low extends NotificationPriority
  case object Normal extends NotificationPriority
  case object High extends NotificationPriority
  case object Critical extends NotificationPriority

  val values = findValues
}

sealed trait DeliveryMethod extends EnumEntry
object DeliveryMethod extends Enum[DeliveryMethod] with CirceEnum[DeliveryMethod] {
  case object InApp extends DeliveryMethod
  case object Email extends DeliveryMethod
  case object SMS extends DeliveryMethod
  case object Push extends DeliveryMethod
  case object Telegram extends DeliveryMethod
  case object WebSocket extends DeliveryMethod

  val values = findValues
}

case class NotificationSettings(
    id: NotificationId,
    userId: PersonId,
    emailNotifications: Boolean,
    pushNotifications: Boolean,
    smsNotifications: Boolean,
    telegramNotifications: Boolean,
    taskAssignments: Boolean,
    taskReminders: Boolean,
    projectUpdates: Boolean,
    teamUpdates: Boolean,
    dailyDigest: Boolean,
    weeklyReport: Boolean,
    productivityInsights: Boolean,
    quietHours: Option[QuietHours],
    timeZone: String,
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
  )

case class QuietHours(
    startTime: LocalTime,
    endTime: LocalTime,
    timeZone: String,
    weekendsOnly: Boolean,
    enabled: Boolean,
  )

case class NotificationTemplate(
    id: NotificationTemplateId,
    name: String,
    notificationType: NotificationType,
    titleTemplate: String,
    contentTemplate: String,
    supportedDeliveryMethods: Set[DeliveryMethod],
    defaultPriority: NotificationPriority,
    variables: List[TemplateVariable],
    isActive: Boolean,
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
  )

case class TemplateVariable(
    name: String,
    description: String,
    required: Boolean,
    defaultValue: Option[String],
  )

case class NotificationRule(
    id: NotificationRuleId,
    userId: PersonId,
    notificationType: NotificationType,
    enabled: Boolean,
    conditions: List[RuleCondition],
    deliveryMethods: Set[DeliveryMethod],
    priority: NotificationPriority,
    cooldownMinutes: Option[Int], // Prevent spam
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
  )

case class RuleCondition(
    field: String,
    operator: ConditionOperator,
    value: String,
  )

sealed trait ConditionOperator extends EnumEntry
object ConditionOperator extends Enum[ConditionOperator] with CirceEnum[ConditionOperator] {
  case object Equals extends ConditionOperator
  case object NotEquals extends ConditionOperator
  case object Contains extends ConditionOperator
  case object GreaterThan extends ConditionOperator
  case object LessThan extends ConditionOperator
  case object In extends ConditionOperator

  val values = findValues
}

case class NotificationDeliveryLog(
    id: java.util.UUID,
    notificationId: NotificationId,
    deliveryMethod: DeliveryMethod,
    status: DeliveryStatus,
    attempts: Int,
    errorMessage: Option[String],
    deliveredAt: Option[ZonedDateTime],
    createdAt: ZonedDateTime,
  )

sealed trait DeliveryStatus extends EnumEntry
object DeliveryStatus extends Enum[DeliveryStatus] with CirceEnum[DeliveryStatus] {
  case object Pending extends DeliveryStatus
  case object Sent extends DeliveryStatus
  case object Delivered extends DeliveryStatus
  case object Failed extends DeliveryStatus
  case object Retrying extends DeliveryStatus

  val values = findValues
}

// Request/Response DTOs
case class CreateNotificationRequest(
    userId: PersonId,
    title: NonEmptyString,
    content: String,
    notificationType: NotificationType,
    relatedEntityId: Option[String] = None,
    relatedEntityType: Option[EntityType] = None,
    priority: NotificationPriority = NotificationPriority.Normal,
    deliveryMethods: Set[DeliveryMethod] = Set(DeliveryMethod.InApp),
    metadata: Map[String, String] = Map.empty,
    scheduledAt: Option[ZonedDateTime] = None,
    actionUrl: Option[String] = None,
    actionLabel: Option[String] = None,
  )

case class NotificationFilters(
    isRead: Option[Boolean] = None,
    notificationType: Option[NotificationType] = None,
    priority: Option[NotificationPriority] = None,
    fromDate: Option[ZonedDateTime] = None,
    toDate: Option[ZonedDateTime] = None,
    limit: Option[Int] = Some(20),
    offset: Option[Int] = Some(0),
  )

case class UpdateNotificationSettingsRequest(
    emailNotifications: Option[Boolean] = None,
    pushNotifications: Option[Boolean] = None,
    smsNotifications: Option[Boolean] = None,
    telegramNotifications: Option[Boolean] = None,
    taskAssignments: Option[Boolean] = None,
    taskReminders: Option[Boolean] = None,
    projectUpdates: Option[Boolean] = None,
    teamUpdates: Option[Boolean] = None,
    dailyDigest: Option[Boolean] = None,
    weeklyReport: Option[Boolean] = None,
    productivityInsights: Option[Boolean] = None,
    quietHours: Option[QuietHours] = None,
    timeZone: Option[String] = None,
  )

case class NotificationStats(
    totalCount: Long,
    unreadCount: Long,
    todayCount: Long,
    weekCount: Long,
    byType: Map[String, Long],
    byPriority: Map[String, Long],
  )

case class BulkNotificationRequest(
    userIds: List[PersonId],
    title: NonEmptyString,
    content: String,
    notificationType: NotificationType,
    priority: NotificationPriority = NotificationPriority.Normal,
    deliveryMethods: Set[DeliveryMethod] = Set(DeliveryMethod.InApp),
    scheduledAt: Option[ZonedDateTime] = None,
  )

// Codecs
object Notification {
  implicit val codec: Codec[Notification] = deriveCodec
}

object NotificationSettings {
  implicit val codec: Codec[NotificationSettings] = deriveCodec
}

object QuietHours {
  implicit val codec: Codec[QuietHours] = deriveCodec
}

object NotificationTemplate {
  implicit val codec: Codec[NotificationTemplate] = deriveCodec
}

object TemplateVariable {
  implicit val codec: Codec[TemplateVariable] = deriveCodec
}

object NotificationRule {
  implicit val codec: Codec[NotificationRule] = deriveCodec
}

object RuleCondition {
  implicit val codec: Codec[RuleCondition] = deriveCodec
}

object NotificationDeliveryLog {
  implicit val codec: Codec[NotificationDeliveryLog] = deriveCodec
}

object CreateNotificationRequest {
  implicit val codec: Codec[CreateNotificationRequest] = deriveCodec
}

object NotificationFilters {
  implicit val codec: Codec[NotificationFilters] = deriveCodec
}

object UpdateNotificationSettingsRequest {
  implicit val codec: Codec[UpdateNotificationSettingsRequest] = deriveCodec
}

object NotificationStats {
  implicit val codec: Codec[NotificationStats] = deriveCodec
}

object BulkNotificationRequest {
  implicit val codec: Codec[BulkNotificationRequest] = deriveCodec
}
