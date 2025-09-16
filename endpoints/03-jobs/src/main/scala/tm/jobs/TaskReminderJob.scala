package tm.jobs

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import cats.effect.Sync
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import tm.JobsEnvironment
import tm.repositories.TasksRepository
import tm.support.jobs.CronJob

object TaskReminderJob extends CronJob[cats.effect.IO, JobsEnvironment[cats.effect.IO]] {
  import cats.effect.IO

  override val name: String = "TaskReminderJob"
  override val logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("TaskReminderJob")

  override def run(implicit env: JobsEnvironment[IO]): IO[Unit] = {
    val now = ZonedDateTime.now()
    val oneDayFromNow = now.plus(1, ChronoUnit.DAYS)

    for {
      _ <- logger.info("Starting task reminder job")
      // This would need to be implemented when we have proper task search by deadline
      // For now, we'll just log that the job is running
      _ <- logger.info(s"Checking for tasks with deadlines approaching at $now")
      _ <- logger.info("Task reminder job completed")
    } yield ()
  }
}
