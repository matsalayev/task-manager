package tm.http

import cats.effect.Async
import tm.Services
import tm.auth.impl.Middlewares
import tm.integration.aws.s3.S3Client
import tm.integrations.telegram.TelegramBotsConfig
import tm.integrations.telegram.TelegramClient
import tm.support.http4s.HttpServerConfig
import tm.support.redis.RedisClient

case class Environment[F[_]: Async](
    config: HttpServerConfig,
    telegramParentsBot: TelegramBotsConfig,
    middlewares: Middlewares[F],
    services: Services[F],
    s3Client: S3Client[F],
    telegramClient: TelegramClient[F],
    redis: RedisClient[F],
  )
