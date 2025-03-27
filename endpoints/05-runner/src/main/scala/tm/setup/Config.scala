package tm.setup

import eu.timepit.refined.types.string.NonEmptyString

import tm.Phone
import tm.auth.AuthConfig
import tm.integration.aws.s3.AWSConfig
import tm.integrations.telegram.TelegramBotsConfig
import tm.support.database.MigrationsConfig
import tm.support.http4s.HttpServerConfig
import tm.support.jobs.JobsRunnerConfig
import tm.support.redis.RedisConfig
import tm.support.skunk.DataBaseConfig

case class Config(
    httpServer: HttpServerConfig,
    database: DataBaseConfig,
    auth: AuthConfig,
    redis: RedisConfig,
    s3: AWSConfig,
    adminPhone: Phone,
    jobs: JobsRunnerConfig,
    tmCorporateBot: TelegramBotsConfig,
    tmEmployeeBot: TelegramBotsConfig,
    appDomain: NonEmptyString,
  ) {
  lazy val migrations: MigrationsConfig = MigrationsConfig(
    hostname = database.host.value,
    port = database.port.value,
    database = database.database.value,
    username = database.user.value,
    password = database.password.value,
    schema = "public",
    location = "db/migration",
  )
}
