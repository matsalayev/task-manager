package tm.services

import cats.MonadThrow
import cats.implicits._
import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.ProjectId
import tm.domain.TagId
import tm.domain.TaskId
import tm.domain.corporate.CreateEmployee
import tm.domain.corporate.User
import tm.domain.enums.Role
import tm.domain.task.CreateTag
import tm.domain.task.CreateTask
import tm.domain.task.Tag
import tm.domain.task.Task
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.exception.AError
import tm.repositories.PeopleRepository
import tm.repositories.TasksRepository
import tm.repositories.UsersRepository
import tm.repositories.dto.Person
import tm.syntax.refined._
import tm.utils.ID

trait EmployeeService[F[_]] {
  def create(data: CreateEmployee, createdBy: PersonId): F[Unit]
}

object EmployeeService {
  def make[F[_]: MonadThrow: GenUUID: Calendar](
      peopleRepository: PeopleRepository[F],
      usersRepository: UsersRepository[F],
    ): EmployeeService[F] =
    new EmployeeService[F] {
      override def create(data: CreateEmployee, createdBy: PersonId): F[Unit] =
        usersRepository
          .findById(createdBy)
          .flatMap(userOpt =>
            userOpt.fold(AError.BadRequest("Foydalanuvchi topilmadi").raiseError[F, Unit]) { user =>
              for {
                id <- ID.make[F, PersonId]
                now <- Calendar[F].currentZonedDateTime
                _ <- peopleRepository.create(
                  Person(
                    id = id,
                    createdAt = now,
                    fullName = data.name,
                    gender = data.gender,
                    dateOfBirth = None,
                    documentNumber = None,
                    pinflNumber = None,
                    updatedAt = None,
                    deletedAt = None,
                  )
                )
                _ <- usersRepository.createUser(
                  User(
                    id = id,
                    createdAt = now,
                    role = Role.Employee,
                    phone = data.phone,
                    assetId = None,
                    corporateId = user.corporateId,
                    password = "$s0$e0801$5JK3Ogs35C2h5htbXQoeEQ==$N7HgNieSnOajn1FuEB7l4PhC6puBSq+e1E8WUaSJcGY=",
                  )
                )
              } yield ()
            }
          )
    }
}
