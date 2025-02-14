package tm.setup

import cats.MonadThrow
import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Console
import cats.effect.std.Random
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import eu.timepit.refined.pureconfig._
import org.typelevel.log4cats.Logger
import pureconfig.generic.auto.exportReader
import sttp.client3.httpclient.fs2.HttpClientFs2Backend

import tm.JobsEnvironment
import tm.Repositories
import tm.Services
import tm.auth.impl.Middlewares
import tm.http.{ Environment => ServerEnvironment }
import tm.integration.aws.s3.S3Client
import tm.integrations.telegram.TelegramClient
import tm.support.database.Migrations
import tm.support.redis.RedisClient
import tm.support.skunk.SkunkSession
import tm.utils.ConfigLoader

case class Environment[F[_]: Async: MonadThrow: Logger](
    config: Config,
    repositories: Repositories[F],
    services: Services[F],
    middlewares: Middlewares[F],
    s3Client: S3Client[F],
    redis: RedisClient[F],
    telegramClient: TelegramClient[F],
  ) {
  lazy val jobsEnabled: Boolean = config.jobs.enabled
  lazy val toServer: ServerEnvironment[F] =
    ServerEnvironment(
      middlewares = middlewares,
      services = services,
      config = config.httpServer,
      s3Client = s3Client,
      telegramClient = telegramClient,
      redis = redis,
      telegramCorporateBot = config.tmCorporateBot,
      telegramEmployeeBot = config.tmEmployeeBot,
    )
  lazy val toJobs: JobsEnvironment[F] =
    JobsEnvironment(
      repos = JobsEnvironment.Repositories(),
      adminPhone = config.adminPhone,
    )
}

object Environment {
  def make[F[_]: Async: Console: Logger]: Resource[F, Environment[F]] =
    for {
      config <- Resource.eval(ConfigLoader.load[F, Config])
      _ <- Resource.eval(Migrations.run[F](config.migrations))

      redis <- Redis[F].utf8(config.redis.uri.toString).map(RedisClient[F](_, config.redis.prefix))
      repositories <- SkunkSession.make[F](config.database).map { implicit session =>
        Repositories.make[F]
      }

      implicit0(random: Random[F]) <- Resource.eval(Random.scalaUtilRandom)
      s3Client <- S3Client.resource(config.s3)
      telegramBroker <- HttpClientFs2Backend.resource[F]().map { implicit backend =>
        TelegramClient.make[F](config.tmEmployeeBot)
      }
      services = Services
        .make[F](config.auth, repositories, redis, s3Client, telegramBroker)
      middleware = Middlewares.make[F](config.auth, redis)
    } yield Environment[F](
      config,
      repositories,
      services,
      middleware,
      s3Client,
      redis,
      telegramBroker,
    )
}
