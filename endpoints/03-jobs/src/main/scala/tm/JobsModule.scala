package tm

import cats.Parallel
import cats.effect.Async
import cats.effect.ExitCode
import cats.effect.Temporal
import cats.implicits._
import cron4s._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import tm.effects.Calendar
import tm.jobs.TaskCleanupJob
import tm.jobs.TaskReminderJob
import tm.jobs.TaskStatusUpdateJob
import tm.support.jobs._

case class JobsModule[F[_]: Async: Parallel: Logger: Temporal](
    env: JobsEnvironment[F]
  )(implicit
    calendar: Calendar[F]
  ) {
  def startJobs(config: JobsRunnerConfig): F[ExitCode] = {
    val cronJobConfigs = List(
      JobsRunnerConfig.CronJobConfig("tm.jobs.TaskReminderJob", "0 0 * * * ?"),
      JobsRunnerConfig.CronJobConfig("tm.jobs.TaskCleanupJob", "0 0 2 * * ?"),
      JobsRunnerConfig.CronJobConfig("tm.jobs.TaskStatusUpdateJob", "0 */15 * * * ?"),
    )

    val configWithJobs = config.copy(
      cronJobs = cronJobConfigs
    )

    new JobsRunner[F, JobsEnvironment[F]](
      env,
      new SingleJobRunner[F, JobsEnvironment[F]],
      new CronJobRunner[F, JobsEnvironment[F]],
      configWithJobs,
    ).run.redeem(_ => ExitCode.Error, _ => ExitCode.Success)
  }
}

object JobsModule {
  def make[F[_]: Async: Parallel: Temporal](
      env: JobsEnvironment[F]
    )(implicit
      calendar: Calendar[F]
    ): F[JobsModule[F]] = {
    implicit val logger: Logger[F] = Slf4jLogger.getLoggerFromName("tm-jobs")
    new JobsModule(env).pure[F]
  }
}
