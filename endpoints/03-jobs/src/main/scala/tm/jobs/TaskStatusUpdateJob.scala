package tm.jobs

import java.time.ZonedDateTime

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import tm.JobsEnvironment
import tm.support.jobs.CronJob

object TaskStatusUpdateJob extends CronJob[cats.effect.IO, JobsEnvironment[cats.effect.IO]] {
  import cats.effect.IO

  override val name: String = "TaskStatusUpdateJob"
  override val logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("TaskStatusUpdateJob")

  override def run(implicit env: JobsEnvironment[IO]): IO[Unit] = {
    val now = ZonedDateTime.now()

    for {
      _ <- logger.info("Starting task status update job")
      // This would automatically update overdue tasks to "Overdue" status
      // For now, we'll just log that the job is running
      _ <- logger.info(s"Checking for overdue tasks at $now")
      _ <- logger.info("Task status update job completed")
    } yield ()
  }
}
