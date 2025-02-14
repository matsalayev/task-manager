package tm

import cats.effect.Async
import cats.effect.Resource
import skunk.Session

import tm.repositories._

case class Repositories[F[_]](
    users: UsersRepository[F],
    people: PeopleRepository[F],
    assetsRepository: AssetsRepository[F],
    telegramRepository: TelegramRepository[F],
    employeeRepository: EmployeeRepository[F],
    corporationsRepository: CorporationsRepository[F],
    projectsRepository: ProjectsRepository[F],
    tasksRepository: TasksRepository[F],
  )

object Repositories {
  def make[F[_]: Async](
      implicit
      session: Resource[F, Session[F]]
    ): Repositories[F] =
    Repositories(
      users = UsersRepository.make[F],
      people = PeopleRepository.make[F],
      assetsRepository = AssetsRepository.make[F],
      telegramRepository = TelegramRepository.make[F],
      employeeRepository = EmployeeRepository.make[F],
      corporationsRepository = CorporationsRepository.make[F],
      projectsRepository = ProjectsRepository.make[F],
      tasksRepository = TasksRepository.make[F],
    )
}
