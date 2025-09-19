package tm.services.notification.providers

import java.util.Properties

import cats.MonadThrow
import cats.effect.Sync
import cats.implicits._

import tm.domain.notifications.DeliveryMethod
import tm.domain.notifications.DeliveryStatus
import tm.domain.notifications.Notification
import tm.repositories.NotificationsRepository
import tm.repositories.UsersRepository

trait EmailNotificationProvider[F[_]] {
  def sendEmail(notification: Notification): F[Unit]
  def sendEmail(
      to: String,
      subject: String,
      content: String,
      isHtml: Boolean = false,
    ): F[Unit]
  def isConfigured: Boolean
  def testConnection(): F[Boolean]
}

case class EmailConfig(
    smtpHost: String,
    smtpPort: Int,
    username: String,
    password: String,
    fromEmail: String,
    fromName: String,
    useTLS: Boolean = true,
    useSSL: Boolean = false,
  )

object EmailNotificationProvider {
  def make[F[_]: MonadThrow: cats.effect.Sync](
      emailConfig: EmailConfig,
      usersRepo: UsersRepository[F],
    ): EmailNotificationProvider[F] = new EmailNotificationProvider[F] {
    // private val session: Session = createEmailSession(emailConfig)  // TODO: Add javax.mail dependency
    override def sendEmail(notification: Notification): F[Unit] =
      for {
        userOpt <- usersRepo.findById(notification.userId)
        user <- userOpt.fold(
          MonadThrow[F].raiseError[tm.domain.corporate.User](new RuntimeException("User not found"))
        )(user => user.asInstanceOf[tm.domain.corporate.User].pure[F]) // TODO: Fix type conversion

        // Get user's email from phone or a separate email field
        userEmail = extractEmailFromUser(user)

        subject = formatSubjectLine(notification.title.value)
        htmlContent = formatEmailContent(notification, user)

        _ <- sendEmail(userEmail, subject, htmlContent, isHtml = true)
      } yield ()

    override def sendEmail(
        to: String,
        subject: String,
        content: String,
        isHtml: Boolean,
      ): F[Unit] = Sync[F].delay {
      // TODO: Implement actual email sending with javax.mail
      println(s"Mock Email: To=$to, Subject=$subject, Content=${content.take(50)}...")
    }

    override def isConfigured: Boolean =
      emailConfig.smtpHost.nonEmpty &&
      emailConfig.username.nonEmpty &&
      emailConfig.fromEmail.nonEmpty

    override def testConnection(): F[Boolean] = Sync[F].delay {
      // TODO: Implement actual connection test with javax.mail
      true // Mock implementation
    }

    private def createEmailSession(config: EmailConfig): Unit = { // TODO: Return Session when javax.mail is available
      val props = new Properties()
      props.put("mail.smtp.host", config.smtpHost)
      props.put("mail.smtp.port", config.smtpPort.toString)
      props.put("mail.smtp.auth", "true")

      if (config.useTLS)
        props.put("mail.smtp.starttls.enable", "true")

      if (config.useSSL)
        props.put("mail.smtp.ssl.enable", "true")

      // Session.getInstance(props, authenticator) // TODO: Implement when javax.mail is available
      ()
    }

    private def extractEmailFromUser(user: tm.domain.corporate.User): String =
      // TODO: Implement proper email extraction
      // For now, assume email is in a specific format or use a default
      s"${user.id.value}@company.com" // Placeholder

    private def formatSubjectLine(title: String): String =
      s"Task Manager - $title"

    private def formatEmailContent(
        notification: Notification,
        user: tm.domain.corporate.User,
      ): String = {
      val actionButton = notification
        .actionUrl
        .map { url =>
          s"""
        <div style="text-align: center; margin: 20px 0;">
          <a href="$url" style="background-color: #007bff; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; display: inline-block;">
            ${notification.actionLabel.getOrElse("View Details")}
          </a>
        </div>
        """
        }
        .getOrElse("")

      s"""
      <!DOCTYPE html>
      <html>
      <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>${notification.title.value}</title>
      </head>
      <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
          <div style="background-color: #f8f9fa; padding: 20px; border-radius: 8px; margin-bottom: 20px;">
              <h1 style="color: #495057; margin-top: 0;">${notification.title.value}</h1>
              <p style="margin-bottom: 0; color: #6c757d;">
                  ${formatNotificationPriority(notification.priority)} | ${formatNotificationType(notification.notificationType)}
              </p>
          </div>

          <div style="background-color: white; padding: 20px; border: 1px solid #dee2e6; border-radius: 8px;">
              <p>${formatNotificationContent(notification.content)}</p>

              $actionButton

              <div style="border-top: 1px solid #dee2e6; padding-top: 15px; margin-top: 15px; font-size: 12px; color: #6c757d;">
                  <p>This notification was sent from Task Manager.</p>
                  <p>To manage your notification preferences, visit your profile settings.</p>
              </div>
          </div>
      </body>
      </html>
      """
    }

    private def formatNotificationContent(content: String): String =
      // Convert plain text to HTML with basic formatting
      content
        .replace("\n", "<br>")
        .replace("**", "<strong>")
        .replace("**", "</strong>") // Basic bold formatting

    private def formatNotificationPriority(
        priority: tm.domain.notifications.NotificationPriority
      ): String = {
      val (text, color) = priority match {
        case tm.domain.notifications.NotificationPriority.Critical => ("🔴 Critical", "#dc3545")
        case tm.domain.notifications.NotificationPriority.High => ("🟠 High", "#fd7e14")
        case tm.domain.notifications.NotificationPriority.Normal => ("🟡 Normal", "#ffc107")
        case tm.domain.notifications.NotificationPriority.Low => ("🟢 Low", "#28a745")
      }
      s"""<span style="color: $color; font-weight: bold;">$text</span>"""
    }

    private def formatNotificationType(
        notificationType: tm.domain.notifications.NotificationType
      ): String =
      notificationType match {
        case tm.domain.notifications.NotificationType.TaskAssigned => "Task Assignment"
        case tm.domain.notifications.NotificationType.TaskDue => "Task Reminder"
        case tm.domain.notifications.NotificationType.TaskOverdue => "Overdue Task"
        case tm.domain.notifications.NotificationType.ProjectUpdate => "Project Update"
        case tm.domain.notifications.NotificationType.TeamUpdate => "Team Update"
        case tm.domain.notifications.NotificationType.DailyGoalReached => "Goal Achievement"
        case tm.domain.notifications.NotificationType.WeeklyGoalReached => "Weekly Goal"
        case tm.domain.notifications.NotificationType.ProductivityInsight => "Productivity Insight"
        case tm.domain.notifications.NotificationType.SystemAlert => "System Alert"
        case _ => "Notification"
      }
  }

  // Mock implementation for development/testing
  def mockProvider[F[_]: MonadThrow]: EmailNotificationProvider[F] =
    new EmailNotificationProvider[F] {
      override def sendEmail(notification: Notification): F[Unit] = {
        println(s"[MOCK EMAIL] To: ${notification.userId}, Subject: ${notification.title.value}")
        println(s"[MOCK EMAIL] Content: ${notification.content}")
        ().pure[F]
      }

      override def sendEmail(
          to: String,
          subject: String,
          content: String,
          isHtml: Boolean,
        ): F[Unit] = {
        println(s"[MOCK EMAIL] To: $to, Subject: $subject")
        println(s"[MOCK EMAIL] Content: $content")
        ().pure[F]
      }

      override def isConfigured: Boolean = true

      override def testConnection(): F[Boolean] = true.pure[F]
    }
}
