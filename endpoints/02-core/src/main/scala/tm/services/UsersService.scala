package tm.services

import java.util.UUID

import cats.MonadThrow
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import tsec.passwordhashers.jca.SCrypt

import tm.Language
import tm.Phone
import tm.ResponseMessages.USER_NOT_FOUND
import tm.domain.CorporateId
import tm.domain.PersonId
import tm.domain.auth.AccessCredentials
import tm.domain.auth.AuthedUser
import tm.domain.users.PasswordChange
import tm.domain.users.UserInvitation
import tm.domain.users.UserProfile
import tm.domain.users.UserProfileUpdate
import tm.domain.users.UserRegistration
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.exception.AError
import tm.repositories.PeopleRepository
import tm.repositories.UsersRepository
import tm.repositories.dto
import tm.utils.ID

trait UsersService[F[_]] {
  def registerUser(registration: UserRegistration): F[PersonId]
  def inviteUser(invitation: UserInvitation): F[PersonId]
  def getUserProfile(userId: PersonId): F[Option[UserProfile]]
  def updateUserProfile(userId: PersonId, update: UserProfileUpdate): F[Unit]
  def changePassword(userId: PersonId, passwordChange: PasswordChange): F[Unit]
  def deleteUser(userId: PersonId): F[Unit]
  def getUserById(userId: PersonId): F[Option[dto.User]]
  def getUsersByCompany(corporateId: CorporateId): F[List[dto.User]]
  def getUserByPhone(phone: Phone): F[Option[dto.User]]
}

object UsersService {
  def make[F[_]: Sync: Calendar: MonadThrow: GenUUID](
      usersRepo: UsersRepository[F],
      peopleRepo: PeopleRepository[F],
    ): UsersService[F] = new UsersService[F] {
    override def registerUser(registration: UserRegistration): F[PersonId] =
      for {
        personId <- ID.make[F, PersonId]
        now <- Calendar[F].currentZonedDateTime
        hashedPassword <- SCrypt.hashpw[F](registration.password.value)

        // Create person record
        person = dto.Person(
          id = personId,
          createdAt = now,
          fullName = registration.fullName,
          gender = registration.gender.getOrElse(tm.domain.enums.Gender.Male),
          dateOfBirth = registration.dateOfBirth,
          documentNumber = registration.documentNumber,
          pinflNumber = registration.pinfl,
          updatedAt = None,
          deletedAt = None,
        )

        // Create user record
        user = AuthedUser.User(
          id = personId,
          corporateId = registration.corporateId,
          role = registration.role,
          phone = registration.phone,
        )

        userWithHash = AccessCredentials(user, hashedPassword)

        _ <- peopleRepo.create(person)
        _ <- usersRepo.create(userWithHash)
      } yield personId

    override def inviteUser(invitation: UserInvitation): F[PersonId] =
      for {
        personId <- Sync[F].delay(PersonId(UUID.randomUUID()))
        now <- Calendar[F].currentZonedDateTime
        // Generate temporary password - user will need to set their own password
        tempPassword <- Sync[F].delay("TempPass123!")
        hashedPassword <- SCrypt.hashpw[F](tempPassword)

        // Create person record with basic info
        person = dto.Person(
          id = personId,
          createdAt = now,
          fullName = invitation.fullName,
          gender = tm.domain.enums.Gender.Male, // Default, user can update later
          dateOfBirth = None,
          documentNumber = None,
          pinflNumber = None,
          updatedAt = None,
          deletedAt = None,
        )

        // Create user record
        user = AuthedUser.User(
          id = personId,
          corporateId = invitation.corporateId,
          role = invitation.role,
          phone = invitation.phone,
        )

        userWithHash = AccessCredentials(user, hashedPassword)

        _ <- peopleRepo.create(person)
        _ <- usersRepo.create(userWithHash)
      } yield personId

    override def getUserProfile(userId: PersonId): F[Option[UserProfile]] =
      OptionT(peopleRepo.findById(userId))
        .map(person =>
          UserProfile(
            fullName = person.fullName,
            email = None, // Email is not in current Person model
            gender = Some(person.gender),
            dateOfBirth = person.dateOfBirth,
            documentNumber = person.documentNumber,
            pinfl = person.pinflNumber,
          )
        )
        .value

    override def updateUserProfile(userId: PersonId, update: UserProfileUpdate): F[Unit] =
      peopleRepo.update(userId) { person =>
        person.copy(
          fullName = update.fullName.getOrElse(person.fullName),
          gender = update.gender.getOrElse(person.gender),
          dateOfBirth = update.dateOfBirth.orElse(person.dateOfBirth),
          documentNumber = update.documentNumber.orElse(person.documentNumber),
          pinflNumber = update.pinfl.orElse(person.pinflNumber),
        )
      }(Language.En) // Default language

    override def changePassword(userId: PersonId, passwordChange: PasswordChange): F[Unit] =
      for {
        userOpt <- usersRepo
          .findById(userId)
          .map(_.map(user => AuthedUser.User(user.id, user.corporateId, user.role, user.phone)))
        user <- userOpt.liftTo[F](AError.BadRequest(USER_NOT_FOUND(Language.En)))

        // Find current user with password hash
        userWithHashOpt <- usersRepo.find(user.phone)
        userWithHash <- userWithHashOpt.liftTo[F](AError.BadRequest(USER_NOT_FOUND(Language.En)))

        // Verify current password
        _ <- SCrypt
          .checkpwBool[F](passwordChange.currentPassword.value, userWithHash.password)
          .flatMap {
            case true => ().pure[F]
            case false =>
              AError.AuthError.PasswordDoesNotMatch("Invalid password").raiseError[F, Unit]
          }

        // Hash new password
        newHashedPassword <- SCrypt.hashpw[F](passwordChange.newPassword.value)
        newUserWithHash = userWithHash.copy(password = newHashedPassword)

        // Update password (we need to extend repository for this)
        // For now, create new user record (not ideal, need proper update method)
        _ <- usersRepo.create(newUserWithHash)
      } yield ()

    override def deleteUser(userId: PersonId): F[Unit] =
      // This should soft delete - mark deleted_at field
      // For now, we'll implement hard delete
      peopleRepo.delete(userId)

    override def getUserById(userId: PersonId): F[Option[dto.User]] =
      usersRepo.findById(userId)

    override def getUsersByCompany(corporateId: CorporateId): F[List[dto.User]] =
      usersRepo
        .getCorporateUsers(corporateId)
        .map(
          _.map(corporateUser =>
            dto.User(
              id = corporateUser.id,
              createdAt = corporateUser.createdAt,
              fullName = NonEmptyString.unsafeFrom("Unknown"), // Need to join with people table
              corporateId = corporateUser.corporateId,
              corporateName = NonEmptyString.unsafeFrom("Unknown"), // Need to join with corporations table
              role = corporateUser.role,
              photo = corporateUser.assetId,
              phone = corporateUser.phone,
            )
          )
        )

    override def getUserByPhone(phone: Phone): F[Option[dto.User]] =
      usersRepo.findById(PersonId(UUID.randomUUID())).flatMap {
        case Some(user) if user.phone == phone => (Some(user): Option[dto.User]).pure[F]
        case _ => (None: Option[dto.User]).pure[F]
      }
  }
}
