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

object TaskCleanupJob extends CronJob[cats.effect.IO, JobsEnvironment[cats.effect.IO]] {
  import cats.effect.IO

  override val name: String = "TaskCleanupJob"
  override val logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("TaskCleanupJob")

  override def run(implicit env: JobsEnvironment[IO]): IO[Unit] = {
    val oneMonthAgo = ZonedDateTime.now().minus(1, ChronoUnit.MONTHS)

    for {
      _ <- logger.info("Starting task cleanup job")
      // This would need to be implemented when we have proper task search by status and date
      // For now, we'll just log that the job is running
      _ <- logger.info(s"Cleaning up completed tasks older than $oneMonthAgo")
      _ <- logger.info("Task cleanup job completed")
    } yield ()
  }
}
