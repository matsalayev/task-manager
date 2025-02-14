package tm.utils

import cats.{Applicative, Show}
import cats.implicits._
import fs2.Pipe
import org.typelevel.log4cats.Logger

trait LoggerPipe[F[_]] {
  def debugPipe[A](f: A => String): fs2.Pipe[F, A, A]
  def infoPipe[A](f: A => String): fs2.Pipe[F, A, A]
  def errorPipe[A](f: Throwable => String): fs2.Pipe[F, Either[Throwable, A], Either[Throwable, A]]
}

object LoggerPipe {
  def apply[F[_]](implicit ev: LoggerPipe[F]): LoggerPipe[F] = ev

  implicit def loggerPipeForLogger[F[_]: Applicative](implicit logger: Logger[F]): LoggerPipe[F] =
    new LoggerPipe[F] {
      def debugPipe[A](f: A => String): fs2.Pipe[F, A, A] =
        _.evalTap(x => logger.debug(f(x)))

      def infoPipe[A](f: A => String): fs2.Pipe[F, A, A] =
        _.evalTap(x => logger.info(f(x)))

      def errorPipe[A](
          f: Throwable => String
        ): Pipe[F, Either[Throwable, A], Either[Throwable, A]] = _.evalTap {
        case Left(error) => logger.error(error)(f(error))
        case Right(_) => ().pure[F]
      }
    }

  implicit class LoggerPipeExtras[F[_]](pipe: LoggerPipe[F]) {
    def processing[A]: Pipe[F, A, A] =
      pipe.infoPipe[A](x => s"Processing: $x")

    def processed[A]: fs2.Pipe[F, A, A] =
      pipe.infoPipe[A](x => s"Processed: $x")

    def processingShow[A: Show]: Pipe[F, A, A] =
      pipe.infoPipe[A](x => s"Processing: ${x.show}")

    def processedShow[A: Show]: Pipe[F, A, A] =
      pipe.infoPipe[A](x => s"Processed: ${x.show}")

    def logListenerError[A]: Pipe[F, Either[Throwable, A], Either[Throwable, A]] =
      pipe.errorPipe[A](err => s"Event-processing error: ${err.getMessage}")

    def wrapWithLogs[A, B](process: fs2.Pipe[F, A, B]): fs2.Pipe[F, A, B] =
      _.through(pipe.processing).through(process).through(pipe.processed)
  }
}
