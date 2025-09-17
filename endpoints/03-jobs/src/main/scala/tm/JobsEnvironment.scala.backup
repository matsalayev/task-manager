package tm

import tm.integrations.telegram.TelegramClient
import tm.repositories.ProjectsRepository
import tm.repositories.TasksRepository
import tm.repositories.UsersRepository

case class JobsEnvironment[F[_]](
    repos: JobsEnvironment.Repositories[F],
    telegram: JobsEnvironment.TelegramClients[F],
    adminPhone: Phone,
  )

object JobsEnvironment {
  case class Repositories[F[_]](
      tasks: TasksRepository[F],
      users: UsersRepository[F],
      projects: ProjectsRepository[F],
    )

  case class TelegramClients[F[_]](
      corporate: TelegramClient[F],
      employee: TelegramClient[F],
    )
}
