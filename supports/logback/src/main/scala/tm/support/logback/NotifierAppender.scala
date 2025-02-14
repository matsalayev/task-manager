package tm.support.logback

import scala.util.Try

import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.generic.auto.exportReader
import sttp.client3.httpclient.fs2.HttpClientFs2Backend

import tm.utils.ConfigLoader

class NotifierAppender[A] extends AppenderBase[A] {
  implicit val logger: SelfAwareStructuredLogger[IO] =
    Slf4jLogger.getLoggerFromName[IO]("[NotifierAppender] ")

  private def notifier: Resource[IO, ErrorNotifier[IO]] =
    HttpClientFs2Backend.resource[IO]().flatMap { implicit backend =>
      for {
        conf <- Resource.eval(ConfigLoader.load[IO, MonitoringConfig])
        notifier = ErrorNotifier[IO](conf)
      } yield notifier
    }

  override def append(eventObject: A): Unit =
    Try {
      eventObject match {
        case loggingEvent: ILoggingEvent =>
          val msg = loggingEvent.getFormattedMessage
          val className = loggingEvent.getLoggerName.split('.').last
          val throwableProxy = loggingEvent.getThrowableProxy
          if (throwableProxy != null)
            s"#TaskManager\n\n$className | $msg | ${throwableProxy.getMessage}"
          else
            s"#TaskManager\n\n$className | $msg"

        case _ =>
          eventObject.toString
      }
    }.fold(
      error => sendNotification(s"${eventObject.toString} | [logging-error] ${error.toString}"),
      sendNotification,
    )

  private def sendNotification(errorText: String): Unit =
    notifier
      .use(_.sendNotification(errorText))
      .handleErrorWith { throwable =>
        logger.warn(throwable)("Error occurred while start notifier appender")
      }
      .unsafeRunAndForget()
}
