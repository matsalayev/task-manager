package tm

import cats.data.OptionT
import cats.effect.Async
import cats.effect.std.Random
import org.typelevel.log4cats.Logger

import tm.auth.AuthConfig
import tm.auth.impl.Auth
import tm.domain.auth.AccessCredentials
import tm.domain.auth.AuthedUser
import tm.integration.aws.s3.S3Client
import tm.integrations.telegram.TelegramClient
import tm.services._
import tm.support.redis.RedisClient

case class Services[F[_]](
    auth: Auth[F, AuthedUser],
    assets: AssetsService[F],
    tasksService: TasksService[F],
    corporateBotService: CorporateBotService[F],
    employeeBotService: EmployeeBotService[F],
    employeeService: EmployeeService[F],
  )

object Services {
  def make[F[_]: Async: Logger: Random](
      config: AuthConfig,
      repositories: Repositories[F],
      redis: RedisClient[F],
      s3Client: S3Client[F],
      telegramClientCorporate: TelegramClient[F],
      telegramClientEmployee: TelegramClient[F],
    ): Services[F] = {
    def findUser: Phone => F[Option[AccessCredentials[AuthedUser]]] = phone =>
      OptionT(repositories.users.find(phone))
        .map(identity[AccessCredentials[AuthedUser]])
        .value

    Services[F](
      auth = Auth.make[F, AuthedUser](config.user, findUser, redis),
      assets = AssetsService.make[F](
        repositories.assetsRepository,
        s3Client,
      ),
      employeeBotService = EmployeeBotService.make[F](
        telegramClientEmployee,
//        repositories.telegramRepository,
//        repositories.corporationsRepository,
//        repositories.projectsRepository,
        redis
      ),
      tasksService = TasksService.make[F](repositories.tasksRepository),
      corporateBotService = CorporateBotService.make[F](
        telegramClientCorporate,
        repositories.telegramRepository,
        repositories.people,
        repositories.users,
        repositories.corporationsRepository,
        repositories.projectsRepository,
        repositories.assetsRepository,
        s3Client,
        redis,
      ),
      employeeService = EmployeeService.make[F](repositories.people, repositories.users),
    )
  }
}
