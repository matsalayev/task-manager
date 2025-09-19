package tm.services.notification.providers

import cats.MonadThrow
import cats.implicits._
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import sttp.client3._
import sttp.client3.circe._

import tm.domain.PersonId
import tm.domain.notifications.DeliveryMethod
import tm.domain.notifications.DeliveryStatus
import tm.domain.notifications.Notification
import tm.repositories.UsersRepository

trait SmsNotificationProvider[F[_]] {
  def sendSms(notification: Notification): F[Unit]
  def sendSms(
      to: String,
      message: String,
    ): F[Unit]
  def isConfigured: Boolean
  def testConnection(): F[Boolean]
}

case class SmsConfig(
    provider: String, // "twilio", "nexmo", "aws-sns", etc.
    apiKey: String,
    apiSecret: String,
    fromNumber: String,
    endpoint: String,
    maxMessageLength: Int = 160,
  )

case class TwilioSmsRequest(
    To: String,
    From: String,
    Body: String,
  )

case class TwilioSmsResponse(
    sid: String,
    status: String,
    errorCode: Option[Int],
    errorMessage: Option[String],
  )

object TwilioSmsRequest {
  implicit val encoder: Encoder[TwilioSmsRequest] = deriveEncoder
}

object TwilioSmsResponse {
  implicit val decoder: Decoder[TwilioSmsResponse] = deriveDecoder
}

object SmsNotificationProvider {
  def make[F[_]: MonadThrow: cats.effect.Sync](
      smsConfig: SmsConfig,
      usersRepo: UsersRepository[F],
      backend: SttpBackend[F, Any],
    ): SmsNotificationProvider[F] = new SmsNotificationProvider[F] {
    override def sendSms(notification: Notification): F[Unit] =
      for {
        userOpt <- usersRepo.findById(notification.userId)
        user <- userOpt.fold(
          MonadThrow[F].raiseError[tm.domain.corporate.User](new RuntimeException("User not found"))
        )(user => user.asInstanceOf[tm.domain.corporate.User].pure[F]) // TODO: Fix type conversion

        // Get user's phone number
        userPhone = extractPhoneFromUser(user)

        // Format message content
        message = formatSmsContent(notification)

        _ <- sendSms(userPhone, message)
      } yield ()

    override def sendSms(to: String, message: String): F[Unit] = {
      val truncatedMessage =
        if (message.length > smsConfig.maxMessageLength)
          message.take(smsConfig.maxMessageLength - 3) + "..."
        else
          message

      smsConfig.provider.toLowerCase match {
        case "twilio" => sendViaTwilio(to, truncatedMessage)
        case "nexmo" => sendViaNexmo(to, truncatedMessage)
        case "aws-sns" => sendViaAwsSns(to, truncatedMessage)
        case _ =>
          MonadThrow[F].raiseError(
            new RuntimeException(s"Unsupported SMS provider: ${smsConfig.provider}")
          )
      }
    }

    override def isConfigured: Boolean =
      smsConfig.apiKey.nonEmpty &&
      smsConfig.apiSecret.nonEmpty &&
      smsConfig.fromNumber.nonEmpty &&
      smsConfig.endpoint.nonEmpty

    override def testConnection(): F[Boolean] = cats
      .effect
      .Sync[F]
      .delay {
        // Simple validation - in production, this could make a test API call
        isConfigured
      }
      .handleError(_ => false)

    private def sendViaTwilio(to: String, message: String): F[Unit] = {
      val request = TwilioSmsRequest(
        To = to,
        From = smsConfig.fromNumber,
        Body = message,
      )

      val authHeader =
        s"Basic ${java.util.Base64.getEncoder.encodeToString(s"${smsConfig.apiKey}:${smsConfig.apiSecret}".getBytes)}"

      val sttpRequest = basicRequest
        .post(uri"${smsConfig.endpoint}")
        .header("Authorization", authHeader)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .body(
          Map(
            "To" -> to,
            "From" -> smsConfig.fromNumber,
            "Body" -> message,
          )
        )
        .response(asJson[TwilioSmsResponse])

      for {
        response <- backend.send(sttpRequest)
        _ <- response.body match {
          case Right(smsResponse) =>
            if (smsResponse.status == "queued" || smsResponse.status == "sent")
              ().pure[F]
            else
              MonadThrow[F].raiseError(
                new RuntimeException(
                  s"SMS sending failed: ${smsResponse.errorMessage.getOrElse("Unknown error")}"
                )
              )
          case Left(error) =>
            MonadThrow[F].raiseError(new RuntimeException(s"Failed to send SMS via Twilio: $error"))
        }
      } yield ()
    }

    private def sendViaNexmo(to: String, message: String): F[Unit] = {
      val requestBody = Map(
        "api_key" -> smsConfig.apiKey,
        "api_secret" -> smsConfig.apiSecret,
        "from" -> smsConfig.fromNumber,
        "to" -> to,
        "text" -> message,
      )

      val sttpRequest = basicRequest
        .post(uri"${smsConfig.endpoint}")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .body(requestBody)
        .response(asStringAlways)

      for {
        response <- backend.send(sttpRequest)
        _ <-
          if (response.body.contains("\"status\":\"0\""))
            ().pure[F]
          else
            MonadThrow[F].raiseError(
              new RuntimeException(s"Failed to send SMS via Nexmo: ${response.body}")
            )
      } yield ()
    }

    private def sendViaAwsSns(to: String, message: String): F[Unit] = {
      // AWS SNS implementation would require AWS SDK integration
      // For now, provide a basic HTTP-based implementation
      val requestBody = Map(
        "PhoneNumber" -> to,
        "Message" -> message,
        "MessageStructure" -> "string",
      ).asJson

      val sttpRequest = basicRequest
        .post(uri"${smsConfig.endpoint}")
        .header("Content-Type", "application/x-amz-json-1.0")
        .header("X-Amz-Target", "AmazonSNS.Publish")
        .body(requestBody.noSpaces)
        .response(asStringAlways)

      for {
        response <- backend.send(sttpRequest)
        _ <-
          if (response.code.isSuccess)
            ().pure[F]
          else
            MonadThrow[F].raiseError(
              new RuntimeException(s"Failed to send SMS via AWS SNS: ${response.body}")
            )
      } yield ()
    }

    private def extractPhoneFromUser(user: tm.domain.corporate.User): String =
      // TODO: Implement proper phone extraction from user model
      // For now, assume phone is stored in a specific format
      user.phone.value // TODO: Handle Option type properly

    private def formatSmsContent(notification: Notification): String = {
      val priorityPrefix = notification.priority match {
        case tm.domain.notifications.NotificationPriority.Critical => "🚨 URGENT: "
        case tm.domain.notifications.NotificationPriority.High => "⚠️ "
        case _ => ""
      }

      val typePrefix = notification.notificationType match {
        case tm.domain.notifications.NotificationType.TaskAssigned => "📋 Task: "
        case tm.domain.notifications.NotificationType.TaskDue => "⏰ Due: "
        case tm.domain.notifications.NotificationType.TaskOverdue => "🔴 Overdue: "
        case tm.domain.notifications.NotificationType.ProjectUpdate => "📊 Project: "
        case tm.domain.notifications.NotificationType.TeamUpdate => "👥 Team: "
        case tm.domain.notifications.NotificationType.SystemAlert => "🔔 Alert: "
        case _ => ""
      }

      val actionSuffix = notification.actionUrl.map(_ => " - Check app for details").getOrElse("")

      s"$priorityPrefix$typePrefix${notification.title.value}\n\n${notification.content}$actionSuffix"
    }
  }

  // Mock implementation for development/testing
  def mockProvider[F[_]: MonadThrow]: SmsNotificationProvider[F] = new SmsNotificationProvider[F] {
    override def sendSms(notification: Notification): F[Unit] = {
      println(s"[MOCK SMS] To: ${notification.userId}, Subject: ${notification.title.value}")
      println(s"[MOCK SMS] Content: ${notification.content}")
      ().pure[F]
    }

    override def sendSms(to: String, message: String): F[Unit] = {
      println(s"[MOCK SMS] To: $to")
      println(s"[MOCK SMS] Message: $message")
      ().pure[F]
    }

    override def isConfigured: Boolean = true

    override def testConnection(): F[Boolean] = true.pure[F]
  }
}
