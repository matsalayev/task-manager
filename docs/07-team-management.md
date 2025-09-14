# Team Management Backend Implementation

## Implementation Tasks

### 1. Team Domain Models
**Priority: 🟡 Medium**
**Fayl**: `endpoints/00-domain/src/main/scala/tm/domain/teams/`

```scala
case class Team(
  id: TeamId,
  name: String,
  description: Option[String],
  companyId: CompanyId,
  leaderId: UserId,
  isActive: Boolean,
  settings: TeamSettings,
  createdAt: Instant,
  updatedAt: Instant
)

case class TeamMember(
  teamId: TeamId,
  userId: UserId,
  role: TeamRole,
  joinedAt: Instant,
  permissions: Set[TeamPermission]
)

case class TeamSettings(
  workHours: WorkHours,
  timeZone: String,
  workingDays: Set[DayOfWeek],
  overtimePolicy: OvertimePolicy,
  breakPolicy: BreakPolicy
)

sealed trait TeamRole
object TeamRole {
  case object Leader extends TeamRole
  case object Senior extends TeamRole
  case object Regular extends TeamRole
  case object Junior extends TeamRole
}

sealed trait TeamPermission
object TeamPermission {
  case object ViewTeamStats extends TeamPermission
  case object ManageMembers extends TeamPermission
  case object AssignTasks extends TeamPermission
  case object ApproveTime extends TeamPermission
}
```

### 2. API Routes
```scala
object TeamRoutes {
  def routes[F[_]: Async](
    teamService: TeamService[F],
    authMiddleware: AuthMiddleware[F]
  ): HttpRoutes[F] = {

    val protectedRoutes: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of[AuthedUser, F] {
      case GET -> Root as user =>
        // List user teams

      case POST -> Root as user =>
        // Create team

      case GET -> Root / UUIDVar(teamId) as user =>
        // Get team details

      case PUT -> Root / UUIDVar(teamId) as user =>
        // Update team

      case POST -> Root / UUIDVar(teamId) / "members" as user =>
        // Add team member

      case DELETE -> Root / UUIDVar(teamId) / "members" / UUIDVar(userId) as user =>
        // Remove team member

      case GET -> Root / UUIDVar(teamId) / "stats" as user =>
        // Team statistics
    }

    authMiddleware(protectedRoutes)
  }
}
```

## Estimated Time: 1-2 hafta