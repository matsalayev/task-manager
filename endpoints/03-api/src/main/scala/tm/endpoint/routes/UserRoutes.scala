package tm.endpoint.routes

import cats.Monad
import cats.MonadThrow
import cats.implicits._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.JsonDecoder
import org.typelevel.log4cats.Logger

import tm.domain.PersonId
import tm.domain.auth.AuthedUser
import tm.domain.users.PasswordChange
import tm.domain.users.UserInvitation
import tm.domain.users.UserProfileUpdate
import tm.domain.users.UserRegistration
import tm.services.UsersService
import tm.support.http4s.utils.Routes
import tm.support.syntax.http4s.http4SyntaxReqOps

final case class UserRoutes[F[_]: Monad: JsonDecoder: MonadThrow](
    usersService: UsersService[F]
  )(implicit
    logger: Logger[F]
  ) extends Routes[F, AuthedUser] {
  override val path = "/users"

  override val public: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case req @ POST -> Root / "register" =>
        req.decodeR[UserRegistration] { registration =>
          usersService.registerUser(registration).flatMap { userId =>
            Created(Map("userId" -> userId.value.toString))
          }
        }

      case req @ POST -> Root / "invite" =>
        req.decodeR[UserInvitation] { invitation =>
          usersService.inviteUser(invitation).flatMap { userId =>
            Created(Map("userId" -> userId.value.toString))
          }
        }
    }

  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {
    // Get current user profile
    case GET -> Root / "profile" as user =>
      usersService.getUserProfile(user.id).flatMap {
        case Some(profile) => Ok(profile)
        case None => NotFound("Profile not found")
      }

    // Update current user profile
    case ar @ PUT -> Root / "profile" as user =>
      ar.req.decodeR[UserProfileUpdate] { update =>
        usersService.updateUserProfile(user.id, update) *> NoContent()
      }

    // Change password
    case ar @ POST -> Root / "change-password" as user =>
      ar.req.decodeR[PasswordChange] { passwordChange =>
        usersService.changePassword(user.id, passwordChange) *> NoContent()
      }

    // Get user by ID (for admins/managers)
    case GET -> Root / UUIDVar(userId) as _ =>
      val targetUserId = PersonId(userId)
      // TODO: Add permission check - only admins or managers from same company
      usersService.getUserById(targetUserId).flatMap {
        case Some(userData) => Ok(userData)
        case None => NotFound("User not found")
      }

    // Get users in company
    case GET -> Root / "company" as user =>
      usersService.getUsersByCompany(user.corporateId).flatMap(users => Ok(users))

    // Delete user (soft delete - for admins only)
    case DELETE -> Root / UUIDVar(userId) as _ =>
      val targetUserId = PersonId(userId)
      // TODO: Add permission check - only admins can delete users
      usersService.deleteUser(targetUserId) *> NoContent()
  }
}
