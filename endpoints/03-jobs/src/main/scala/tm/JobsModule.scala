package tm

import cats.Parallel
import cats.effect.Async
import cats.effect.ExitCode
import cats.effect.Temporal
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import tm.support.jobs._
case class JobsModule[F[_]: Async: Parallel: Logger: Temporal](
    env: JobsEnvironment[F]
  ) {
  def startJobs(config: JobsRunnerConfig): F[ExitCode] =
    new JobsRunner[F, JobsEnvironment[F]](
      env,
      new SingleJobRunner[F, JobsEnvironment[F]],
      new CronJobRunner[F, JobsEnvironment[F]],
      config,
    ).run.redeem(_ => ExitCode.Error, _ => ExitCode.Success)
}

object JobsModule {
  def make[F[_]: Async: Parallel: Temporal](
      env: JobsEnvironment[F]
    ): F[JobsModule[F]] = {
    implicit val logger: Logger[F] = Slf4jLogger.getLoggerFromName("tm-jobs")
    new JobsModule(env).pure[F]
  }
}
