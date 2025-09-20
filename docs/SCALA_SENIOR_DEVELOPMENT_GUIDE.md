# Universal Scala Backend Development Guide - Senior Level

## 🎯 Maqsad
Bu qo'llanma senior Scala developerlari uchun functional programming va layered architecture asosida qurilgan enterprise backend tizimlarda ishlash uchun mo'ljallangan.

## 📚 Asosiy Tech Stack
- **Language**: Scala 2.13+ with Functional Programming
- **Effects System**: Cats Effect 3.x
- **Database**: PostgreSQL + Skunk (type-safe SQL)
- **Web Framework**: Http4s + Circe (JSON)
- **Architecture**: Clean Architecture with Layers
- **Build Tool**: sbt
- **Migration**: Flyway
- **Testing**: Weaver-test + TestContainers

## 🏗️ Project Architecture

### Standard Module Structure
```
project-root/
├── build.sbt                      # Main build configuration
├── project/
│   ├── Dependencies.scala         # Centralized dependency management
│   └── plugins.sbt               # sbt plugins
├── common/                       # Shared utilities and types
├── endpoints/
│   ├── 00-domain/               # Domain models, type-safe IDs, enums
│   ├── 01-repos/                # Repository layer, SQL, migrations
│   ├── 02-core/                 # Business services, logic
│   ├── 03-api/                  # HTTP routes, endpoints
│   ├── 03-jobs/                 # Background jobs, scheduling
│   ├── 04-server/               # Server setup, routing
│   └── 05-runner/               # Application entry point
├── supports/                    # Infrastructure support modules
├── integrations/               # External service integrations
└── test-tools/                 # Testing utilities and generators
```

---

## 🚨 **CRITICAL DEVELOPMENT PATTERNS - MANDATORY USAGE**

### **ID Generation Pattern**
```scala
// ✅ ALWAYS use this pattern for entity creation
import projectname.utils.ID

// In Service layer
override def createEntity(input: EntityInput): F[Entity] =
  for {
    id <- ID.make[F, EntityId]  // ✅ REQUIRED: Use ID.make[F, TypeId]
    now <- Calendar[F].currentZonedDateTime
    entity = Entity(id = id, ...)
    _ <- repository.save(entity)
  } yield entity
```

### **Error Handling Pattern**
```scala
// ✅ ALWAYS use this pattern for business logic errors
import projectname.exception.AError

// In Service layer
override def findEntityById(id: EntityId)(implicit lang: Language): F[Entity] =
  repository.findById(id).flatMap {
    case Some(entity) => entity.pure[F]
    case None => AError.BadRequest(ENTITY_NOT_FOUND(lang)).raiseError[F, Entity]  // ✅ REQUIRED: Use AError.raiseError
  }

// For validation errors
override def validateInput(input: EntityInput): F[EntityInput] =
  if (input.isValid) input.pure[F]
  else AError.BadRequest("Invalid input").raiseError[F, EntityInput]
```

---

## 🔥 Feature Development Workflow

### Phase 1: Database Schema Design

#### 1.1 Migration Creation
```bash
# Find next migration number
ls endpoints/01-repos/src/main/resources/db/migration/ | tail -5

# Create migration file
# Format: V{sequential_number}__descriptive_name.sql
# Example: V042__create_user_settings_table.sql
```

#### 1.2 Migration Best Practices
```sql
-- ✅ Good Migration Pattern
CREATE TYPE entity_status AS ENUM ('active', 'inactive', 'pending', 'archived');

CREATE TABLE user_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    setting_key VARCHAR(100) NOT NULL,
    setting_value JSONB NOT NULL,
    status entity_status NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,

    -- Performance indexes
    CONSTRAINT uk_user_settings_key UNIQUE(user_id, setting_key) WHERE deleted_at IS NULL
);

-- Essential indexes for performance
CREATE INDEX idx_user_settings_user_id ON user_settings(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_settings_status ON user_settings(status);
CREATE INDEX idx_user_settings_created_at ON user_settings(created_at DESC);

-- Partial index for soft-deleted records
CREATE INDEX idx_user_settings_deleted ON user_settings(deleted_at) WHERE deleted_at IS NOT NULL;
```

### Phase 2: Domain Model Design

#### 2.1 Type-Safe ID Generation
```scala
// endpoints/00-domain/src/main/scala/projectname/domain/package.scala
import io.estatico.newtype.macros.newtype
import derevo.derive
import derevo.cats.{eqv, show}
import java.util.UUID

// ✅ Standard ID Pattern
@derive(eqv, show, uuid)
@newtype case class UserSettingId(value: UUID)
```

#### 2.2 Core Domain Models
```scala
// endpoints/00-domain/src/main/scala/projectname/domain/user_settings/UserSetting.scala
package projectname.domain.user_settings

import java.time.ZonedDateTime
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.JsonCodec
import io.circe.refined._
import projectname.domain.{UserSettingId, UserId}
import projectname.syntax.circe._

// ✅ Primary Domain Entity
@JsonCodec
case class UserSetting(
  id: UserSettingId,
  userId: UserId,
  settingKey: NonEmptyString,
  settingValue: io.circe.Json,
  status: SettingStatus,
  createdAt: ZonedDateTime,
  updatedAt: ZonedDateTime,
  deletedAt: Option[ZonedDateTime] = None
)

// ✅ Input Model for Creation/Updates
@JsonCodec
case class UserSettingInput(
  userId: UserId,
  settingKey: NonEmptyString,
  settingValue: io.circe.Json,
  status: SettingStatus = SettingStatus.Active
)

// ✅ Info Model with Joined Data
@JsonCodec
case class UserSettingInfo(
  id: UserSettingId,
  user: User, // Joined user information
  settingKey: NonEmptyString,
  settingValue: io.circe.Json,
  status: SettingStatus,
  createdAt: ZonedDateTime,
  updatedAt: ZonedDateTime
)

// ✅ Filters for Queries
@JsonCodec
case class UserSettingFilters(
  userId: Option[UserId] = None,
  settingKey: Option[NonEmptyString] = None,
  status: Option[SettingStatus] = None,
  createdAtFrom: Option[ZonedDateTime] = None,
  createdAtTo: Option[ZonedDateTime] = None,
  limit: Option[PosInt] = Some(50.refined),
  page: Option[PosInt] = Some(1.refined)
)

// ✅ Enum Definition
sealed trait SettingStatus extends EnumEntry
object SettingStatus extends Enum[SettingStatus] with CatsEnum[SettingStatus] {
  case object Active extends SettingStatus
  case object Inactive extends SettingStatus
  case object Pending extends SettingStatus
  case object Archived extends SettingStatus

  val values = findValues
}
```

### Phase 3: SQL Layer Implementation

#### 3.1 Skunk SQL Patterns
```scala
// endpoints/01-repos/src/main/scala/projectname/repositories/sql/UserSettingSql.scala
package projectname.repositories.sql

import skunk._
import skunk.codec.all._
import skunk.implicits._
import projectname.domain.UserSettingId
import projectname.domain.user_settings._
import projectname.support.skunk.Sql
import projectname.support.skunk.codecs.{nes, zonedDateTime, settingStatus}
import projectname.support.skunk.syntax.all.skunkSyntaxFragmentOps

private[repositories] object UserSettingSql extends Sql[UserSettingId] {

  // ✅ Codec Definition
  private[repositories] val codec: Codec[UserSetting] =
    (id *: UsersSql.id *: nes *: jsonb *: settingStatus *: zonedDateTime *: zonedDateTime *: zonedDateTime.opt)
      .to[UserSetting]

  // ✅ UPSERT Command (Handle conflicts gracefully)
  val upsert: Command[UserSetting] =
    sql"""
      INSERT INTO user_settings (id, user_id, setting_key, setting_value, status, created_at, updated_at, deleted_at)
      VALUES ($codec)
      ON CONFLICT (user_id, setting_key) WHERE deleted_at IS NULL
      DO UPDATE SET
        setting_value = EXCLUDED.setting_value,
        status = EXCLUDED.status,
        updated_at = EXCLUDED.updated_at
    """.command

  // ✅ Find by ID
  val findById: Query[UserSettingId, UserSetting] =
    sql"""
      SELECT id, user_id, setting_key, setting_value, status, created_at, updated_at, deleted_at
      FROM user_settings
      WHERE id = $id AND deleted_at IS NULL
      LIMIT 1
    """.query(codec)

  // ✅ Dynamic Query Builder with Filters
  def getByFilters(filters: UserSettingFilters): AppliedFragment = {
    val searchFilters: List[Option[AppliedFragment]] = List(
      filters.userId.map(sql"user_id = ${UsersSql.id}"),
      filters.settingKey.map(sql"setting_key = $nes"),
      filters.status.map(sql"status = $settingStatus"),
      filters.createdAtFrom.map(sql"created_at >= $zonedDateTime"),
      filters.createdAtTo.map(sql"created_at <= $zonedDateTime")
    )

    val baseQuery = void"""
      SELECT id, user_id, setting_key, setting_value, status, created_at, updated_at, deleted_at,
             COUNT(*) OVER() as total_count
      FROM user_settings
      WHERE deleted_at IS NULL
    """

    baseQuery
      .andOpt(searchFilters)
      |+| void" ORDER BY created_at DESC"
  }

  // ✅ Soft Delete
  val softDelete: Command[UserSettingId] =
    sql"""
      UPDATE user_settings
      SET deleted_at = NOW(), updated_at = NOW()
      WHERE id = $id AND deleted_at IS NULL
    """.command

  // ✅ Find by User and Key
  val findByUserAndKey: Query[(UserId, NonEmptyString), UserSetting] =
    sql"""
      SELECT id, user_id, setting_key, setting_value, status, created_at, updated_at, deleted_at
      FROM user_settings
      WHERE user_id = ${UsersSql.id} AND setting_key = $nes AND deleted_at IS NULL
      LIMIT 1
    """.query(codec)
}
```

### Phase 4: Repository Layer

#### 4.1 Repository Interface and Implementation
```scala
// endpoints/01-repos/src/main/scala/projectname/repositories/UserSettingRepository.scala
package projectname.repositories

import cats.effect.Resource
import cats.implicits.{catsSyntaxApplicativeId, toFunctorOps, toTraverseOps}
import skunk._
import skunk.codec.all.int8
import eu.timepit.refined.types.string.NonEmptyString
import projectname.domain.{PaginatedResponse, UserSettingId, UserId}
import projectname.domain.user_settings.{UserSetting, UserSettingFilters}
import projectname.repositories.sql.UserSettingSql
import projectname.support.skunk.syntax.all._

// ✅ Repository Trait Definition
trait UserSettingRepository[F[_]] {
  def upsertUserSetting(setting: UserSetting): F[Unit]
  def findUserSettingById(id: UserSettingId): F[Option[UserSetting]]
  def findByUserAndKey(userId: UserId, key: NonEmptyString): F[Option[UserSetting]]
  def getUserSettingsByFilters(filters: UserSettingFilters): F[PaginatedResponse[UserSetting]]
  def deleteUserSetting(id: UserSettingId): F[Unit]
  def getUserSettingsByUserId(userId: UserId): F[List[UserSetting]]
}

// ✅ Repository Implementation
object UserSettingRepository {
  def make[F[_]: fs2.Compiler.Target](
    implicit session: Resource[F, Session[F]]
  ): UserSettingRepository[F] = new UserSettingRepository[F] {

    override def upsertUserSetting(setting: UserSetting): F[Unit] =
      UserSettingSql.upsert.execute(setting)

    override def findUserSettingById(id: UserSettingId): F[Option[UserSetting]] =
      UserSettingSql.findById.queryOption(id)

    override def findByUserAndKey(userId: UserId, key: NonEmptyString): F[Option[UserSetting]] =
      UserSettingSql.findByUserAndKey.queryOption((userId, key))

    override def getUserSettingsByFilters(
      filters: UserSettingFilters
    ): F[PaginatedResponse[UserSetting]] = {
      val af = UserSettingSql.getByFilters(filters).paginateOpt(filters.limit, filters.page)
      for {
        results <- af.fragment.query(UserSettingSql.codec *: int8).queryList(af.argument)
        settings = results.map(_._1)
        totalCount = results.headOption.fold(0L)(_._2)
      } yield PaginatedResponse(settings, totalCount)
    }

    override def deleteUserSetting(id: UserSettingId): F[Unit] =
      UserSettingSql.softDelete.execute(id)

    override def getUserSettingsByUserId(userId: UserId): F[List[UserSetting]] =
      UserSettingSql.findByUser.queryList(userId)
  }
}
```

### Phase 5: Service Layer (Business Logic)

#### 5.1 Error Messages Management
```scala
// common/src/main/scala/projectname/ResponseMessages.scala
val USER_SETTING_NOT_FOUND: Map[Language, String] = Map(
  En -> "User setting not found",
  Ru -> "Настройка пользователя не найдена",
  Uz -> "Foydalanuvchi sozlamasi topilmadi"
)

val INVALID_SETTING_VALUE: Map[Language, String] = Map(
  En -> "Invalid setting value provided",
  Ru -> "Предоставлено недопустимое значение настройки",
  Uz -> "Noto'g'ri sozlama qiymati berilgan"
)
```

#### 5.2 Service Implementation
```scala
// endpoints/02-core/src/main/scala/projectname/services/UserSettingService.scala
package projectname.services

import cats.MonadThrow
import cats.data.OptionT
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import projectname.Language
import projectname.ResponseMessages._
import projectname.domain.{PaginatedResponse, UserSettingId, UserId}
import projectname.domain.user_settings._
import projectname.effects.{Calendar, GenUUID}
import projectname.exception.AError
import projectname.repositories.{UserSettingRepository, UsersRepository}
import projectname.utils.ID

// ✅ Service Interface
trait UserSettingService[F[_]] {
  def upsertUserSetting(input: UserSettingInput): F[UserSetting]
  def getUserSettingById(id: UserSettingId)(implicit lang: Language): F[UserSettingInfo]
  def getUserSettingByKey(userId: UserId, key: NonEmptyString)(implicit lang: Language): F[UserSetting]
  def getUserSettingsByFilters(filters: UserSettingFilters): F[PaginatedResponse[UserSettingInfo]]
  def deleteUserSetting(id: UserSettingId)(implicit lang: Language): F[Unit]
  def getUserSettings(userId: UserId): F[List[UserSetting]]
}

// ✅ Service Implementation
object UserSettingService {
  def make[F[_]: MonadThrow: GenUUID: Calendar](
    repository: UserSettingRepository[F],
    usersRepository: UsersRepository[F]
  ): UserSettingService[F] = new UserSettingService[F] {

    override def upsertUserSetting(input: UserSettingInput): F[UserSetting] =
      for {
        id <- ID.make[F, UserSettingId]
        now <- Calendar[F].currentZonedDateTime
        setting = UserSetting(
          id = id,
          userId = input.userId,
          settingKey = input.settingKey,
          settingValue = input.settingValue,
          status = input.status,
          createdAt = now,
          updatedAt = now,
          deletedAt = None
        )
        _ <- repository.upsertUserSetting(setting)
      } yield setting

    override def getUserSettingById(id: UserSettingId)(implicit lang: Language): F[UserSettingInfo] =
      (for {
        setting <- OptionT(repository.findUserSettingById(id))
        user <- OptionT(usersRepository.findById(setting.userId))
      } yield UserSettingInfo(
        id = setting.id,
        user = user,
        settingKey = setting.settingKey,
        settingValue = setting.settingValue,
        status = setting.status,
        createdAt = setting.createdAt,
        updatedAt = setting.updatedAt
      )).getOrElseF(
        AError.BadRequest(USER_SETTING_NOT_FOUND(lang)).raiseError[F, UserSettingInfo]
      )

    override def getUserSettingByKey(userId: UserId, key: NonEmptyString)(implicit lang: Language): F[UserSetting] =
      repository.findByUserAndKey(userId, key).flatMap {
        case Some(setting) => setting.pure[F]
        case None => AError.BadRequest(USER_SETTING_NOT_FOUND(lang)).raiseError[F, UserSetting]
      }

    override def getUserSettingsByFilters(
      filters: UserSettingFilters
    ): F[PaginatedResponse[UserSettingInfo]] =
      for {
        paginated <- repository.getUserSettingsByFilters(filters)
        userIds = paginated.data.map(_.userId).distinct
        usersMap <- usersRepository.findByIds(userIds).map(_.map(u => u.id -> u).toMap)
        infos = paginated.data.map { setting =>
          UserSettingInfo(
            id = setting.id,
            user = usersMap(setting.userId),
            settingKey = setting.settingKey,
            settingValue = setting.settingValue,
            status = setting.status,
            createdAt = setting.createdAt,
            updatedAt = setting.updatedAt
          )
        }
      } yield PaginatedResponse(infos, paginated.total)

    override def deleteUserSetting(id: UserSettingId)(implicit lang: Language): F[Unit] =
      repository.findUserSettingById(id).flatMap {
        case Some(_) => repository.deleteUserSetting(id)
        case None => AError.BadRequest(USER_SETTING_NOT_FOUND(lang)).raiseError[F, Unit]
      }

    override def getUserSettings(userId: UserId): F[List[UserSetting]] =
      repository.getUserSettingsByUserId(userId)
  }
}
```

### Phase 6: API Layer

#### 6.1 Privilege-based Authorization
```scala
// endpoints/00-domain/src/main/scala/projectname/domain/enums/Privilege.scala
sealed trait Privilege extends EnumEntry {
  def group: String
}

object Privilege extends Enum[Privilege] with CatsEnum[Privilege] {
  // User Settings privileges
  case object CreateUserSetting extends Privilege { override val group = "USER_SETTING" }
  case object ViewUserSettings extends Privilege { override val group = "USER_SETTING" }
  case object UpdateUserSetting extends Privilege { override val group = "USER_SETTING" }
  case object DeleteUserSetting extends Privilege { override val group = "USER_SETTING" }

  val values = findValues
}
```

#### 6.2 HTTP Routes Implementation
```scala
// endpoints/03-api/src/main/scala/projectname/endpoint/routes/UserSettingRoutes.scala
package projectname.endpoint.routes

import cats.MonadThrow
import cats.implicits._
import io.estatico.newtype.ops.toCoercibleIdOps
import org.http4s._
import org.http4s.circe.JsonDecoder
import eu.timepit.refined.types.string.NonEmptyString
import projectname.Language
import projectname.domain.{UserSettingId, UserId}
import projectname.domain.auth.AuthedUser
import projectname.domain.enums.Privilege
import projectname.domain.user_settings.{UserSettingFilters, UserSettingInput}
import projectname.domain.users.Role
import projectname.services.UserSettingService
import projectname.support.http4s.utils.Routes
import projectname.support.syntax.all.deriveEntityEncoder
import projectname.support.syntax.http4s.http4SyntaxReqOps
import projectname.syntax.circe._

final case class UserSettingRoutes[F[_]: JsonDecoder: MonadThrow](
  userSettingService: UserSettingService[F]
) extends Routes[F, AuthedUser] {

  override val path = "/user-settings"

  override val public: HttpRoutes[F] = HttpRoutes.empty[F]

  override val `private`: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of {

    // ✅ Create/Update User Setting
    case ar @ POST -> Root as user =>
      implicit val lang: Language = ar.req.lang
      implicit val role: Role = user.role
      authorize(Privilege.CreateUserSetting) {
        ar.req.decodeR[UserSettingInput] { input =>
          userSettingService.upsertUserSetting(input).flatMap(Created(_))
        }
      }

    // ✅ Get User Setting by ID
    case ar @ GET -> Root / UUIDVar(id) as user =>
      implicit val lang: Language = ar.req.lang
      implicit val role: Role = user.role
      authorize(Privilege.ViewUserSettings) {
        userSettingService.getUserSettingById(id.coerce[UserSettingId]).flatMap(Ok(_))
      }

    // ✅ Get User Setting by Key
    case ar @ GET -> Root / "user" / UUIDVar(userId) / "key" / key as user =>
      implicit val lang: Language = ar.req.lang
      implicit val role: Role = user.role
      authorize(Privilege.ViewUserSettings) {
        NonEmptyString.from(key) match {
          case Right(validKey) =>
            userSettingService.getUserSettingByKey(userId.coerce[UserId], validKey).flatMap(Ok(_))
          case Left(_) =>
            BadRequest("Invalid setting key provided")
        }
      }

    // ✅ Get User Settings with Filters
    case ar @ POST -> Root / "search" as user =>
      implicit val lang: Language = ar.req.lang
      implicit val role: Role = user.role
      authorize(Privilege.ViewUserSettings) {
        ar.req.decodeR[UserSettingFilters] { filters =>
          userSettingService.getUserSettingsByFilters(filters).flatMap(Ok(_))
        }
      }

    // ✅ Get All Settings for User
    case ar @ GET -> Root / "user" / UUIDVar(userId) as user =>
      implicit val lang: Language = ar.req.lang
      implicit val role: Role = user.role
      authorize(Privilege.ViewUserSettings) {
        userSettingService.getUserSettings(userId.coerce[UserId]).flatMap(Ok(_))
      }

    // ✅ Delete User Setting
    case ar @ DELETE -> Root / UUIDVar(id) as user =>
      implicit val lang: Language = ar.req.lang
      implicit val role: Role = user.role
      authorize(Privilege.DeleteUserSetting) {
        userSettingService.deleteUserSetting(id.coerce[UserSettingId]).flatMap(Ok(_))
      }
  }
}
```

### Phase 7: Testing Strategy

#### 7.1 Repository Testing Pattern
```scala
// endpoints/01-repos/src/test/scala/projectname/repositories/UserSettingRepositorySpec.scala
package projectname.repositories

import cats.effect.IO
import cats.effect.Resource
import skunk.Session
import eu.timepit.refined.types.string.NonEmptyString
import projectname.Language
import projectname.database.DBSuite
import projectname.generators.{Generators, UserSettingGenerators, UserGenerators}

object UserSettingRepositorySpec
    extends DBSuite
       with Generators
       with UserSettingGenerators
       with UserGenerators {

  override def schemaName: String = "public"
  override def beforeAll(implicit session: Resource[IO, Session[IO]]): IO[Unit] = data.setup

  implicit val lang: Language = Language.En

  test("Insert and find user setting") { implicit session =>
    val repo = UserSettingRepository.make[F]
    val setting = userSettingGen(data.users.user1.id).gen

    for {
      _ <- repo.upsertUserSetting(setting)
      retrieved <- repo.findUserSettingById(setting.id)
    } yield assert(
      retrieved.isDefined &&
      retrieved.get.id == setting.id &&
      retrieved.get.settingKey == setting.settingKey
    )
  }

  test("Find setting by user and key") { implicit session =>
    val repo = UserSettingRepository.make[F]
    val setting = userSettingGen(data.users.user1.id).gen

    for {
      _ <- repo.upsertUserSetting(setting)
      found <- repo.findByUserAndKey(setting.userId, setting.settingKey)
    } yield assert(found.exists(_.id == setting.id))
  }

  test("Upsert overwrites existing setting") { implicit session =>
    val repo = UserSettingRepository.make[F]
    val key = NonEmptyString.unsafeFrom("test_key")
    val setting1 = userSettingGen(data.users.user1.id).gen.copy(settingKey = key)
    val setting2 = userSettingGen(data.users.user1.id).gen.copy(settingKey = key)

    for {
      _ <- repo.upsertUserSetting(setting1)
      _ <- repo.upsertUserSetting(setting2)
      result <- repo.findByUserAndKey(setting1.userId, key)
    } yield assert(
      result.isDefined &&
      result.get.settingValue == setting2.settingValue
    )
  }

  test("Soft delete setting") { implicit session =>
    val repo = UserSettingRepository.make[F]
    val setting = userSettingGen(data.users.user1.id).gen

    for {
      _ <- repo.upsertUserSetting(setting)
      _ <- repo.deleteUserSetting(setting.id)
      deleted <- repo.findUserSettingById(setting.id)
    } yield assert(deleted.isEmpty)
  }

  test("Filter settings by user") { implicit session =>
    val repo = UserSettingRepository.make[F]
    val filters = UserSettingFilters(userId = Some(data.users.user1.id))

    for {
      results <- repo.getUserSettingsByFilters(filters)
    } yield assert(results.data.forall(_.userId == data.users.user1.id))
  }
}
```

#### 7.2 Service Testing Pattern
```scala
// endpoints/02-core/src/test/scala/projectname/services/UserSettingServiceSpec.scala
package projectname.services

import cats.effect.IO
import weaver.SimpleIOSuite
import cats.effect.unsafe.implicits.global
import eu.timepit.refined.types.string.NonEmptyString
import projectname.Language
import projectname.domain.user_settings.UserSettingInput
import projectname.effects.TestCalendar
import projectname.repositories.mocks.{MockUserSettingRepository, MockUsersRepository}

object UserSettingServiceSpec extends SimpleIOSuite {
  implicit val lang: Language = Language.En

  test("Create user setting successfully") {
    val userRepo = new MockUsersRepository[IO]()
    val settingRepo = new MockUserSettingRepository[IO]()
    val service = UserSettingService.make[IO](settingRepo, userRepo)

    val input = UserSettingInput(
      userId = TestData.users.user1.id,
      settingKey = NonEmptyString.unsafeFrom("theme"),
      settingValue = io.circe.Json.fromString("dark"),
      status = SettingStatus.Active
    )

    service.upsertUserSetting(input).map { result =>
      expect(result.settingKey == input.settingKey) and
      expect(result.userId == input.userId)
    }
  }
}
```

---

## 🚀 Senior Developer Best Practices

### 1. **Type Safety First**
```scala
// ✅ Always use newtype for IDs
@newtype case class UserId(value: UUID)

// ✅ Use refined types for validation
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.types.numeric.PosInt

// ✅ Algebraic Data Types for state
sealed trait ProcessStatus
object ProcessStatus {
  case object Pending extends ProcessStatus
  case object InProgress extends ProcessStatus
  case object Completed extends ProcessStatus
  case object Failed extends ProcessStatus
}
```

### 2. **Error Handling Patterns**
```scala
// ✅ Use custom error types
sealed trait ServiceError
object ServiceError {
  case class NotFound(message: String) extends ServiceError
  case class ValidationError(field: String, message: String) extends ServiceError
  case class DatabaseError(cause: Throwable) extends ServiceError
}

// ✅ Return Either for business logic errors
def findUser(id: UserId): F[Either[ServiceError, User]] = ???

// ✅ Use OptionT for chaining optional operations
def getUserWithProfile(id: UserId): F[Option[UserWithProfile]] =
  (for {
    user <- OptionT(userRepo.findById(id))
    profile <- OptionT(profileRepo.findByUserId(id))
  } yield UserWithProfile(user, profile)).value
```

### 3. **Performance Optimization**
```scala
// ✅ Batch database queries
def getUsersWithProfiles(userIds: List[UserId]): F[List[UserWithProfile]] =
  for {
    users <- userRepo.findByIds(userIds)
    profiles <- profileRepo.findByUserIds(userIds)
    profileMap = profiles.map(p => p.userId -> p).toMap
  } yield users.map(u => UserWithProfile(u, profileMap.get(u.id)))

// ✅ Use streaming for large datasets
import fs2.Stream

def processLargeDataset: Stream[F, ProcessedItem] =
  dataRepo.streamAll(1000) // chunk size
    .evalMap(processItem)
    .handleErrorWith(logAndContinue)
```

### 4. **Testing Excellence**
```scala
// ✅ Property-based testing
test("User settings maintain invariants") {
  forall(userSettingGen) { setting =>
    expect(setting.createdAt <= setting.updatedAt) and
    expect(setting.settingKey.value.nonEmpty) and
    expect(setting.deletedAt.isEmpty || setting.status == SettingStatus.Archived)
  }
}

// ✅ Integration testing with TestContainers
trait DatabaseSpec extends IOSuite {
  override def sharedResource: Resource[IO, PostgreSQLContainer] =
    Resource.make(IO(PostgreSQLContainer()))(c => IO(c.stop()))
}
```

### 5. **Monitoring and Observability**
```scala
import org.typelevel.log4cats.Logger

// ✅ Structured logging
def processRequest[F[_]: Logger](request: Request): F[Response] =
  for {
    _ <- Logger[F].info(s"Processing request ${request.id}")
    result <- businessLogic(request)
    _ <- Logger[F].info(s"Request ${request.id} completed successfully")
  } yield result

// ✅ Metrics tracking
import io.micrometer.core.instrument.MeterRegistry

class UserService[F[_]](metrics: MeterRegistry) {
  private val userCreationCounter = metrics.counter("user.creation.count")

  def createUser(input: UserInput): F[User] =
    for {
      user <- userRepo.create(input)
      _ <- Sync[F].delay(userCreationCounter.increment())
    } yield user
}
```

### 6. **Security Best Practices**
```scala
// ✅ Input validation
def validateUserInput(input: UserInput): F[ValidatedNel[ValidationError, UserInput]] = {
  val validations = List(
    validateEmail(input.email),
    validatePassword(input.password),
    validateAge(input.age)
  )
  validations.sequence.map(_.reduce)
}

// ✅ Authorization middleware
def requireRole(role: Role): AuthMiddleware[F, User] =
  AuthMiddleware.withFallThrough(user =>
    if (user.role == role) F.pure(Some(user))
    else F.pure(None)
  )
```

---

## 📋 Development Checklist

### ✅ Before Starting Feature Development
- [ ] Read existing similar features for patterns
- [ ] Check database schema and existing migrations
- [ ] Identify domain models and relationships
- [ ] Plan API endpoints and authorization needs
- [ ] Design test strategy

### ✅ During Development
- [ ] Follow exact naming conventions
- [ ] Use proper type safety (newtype, refined)
- [ ] Implement proper error handling
- [ ] Add database indexes for performance
- [ ] Use soft delete pattern
- [ ] Implement proper authorization
- [ ] Add comprehensive logging

### ✅ Before Code Review
- [ ] Write comprehensive tests (unit + integration)
- [ ] Add proper documentation comments
- [ ] Check performance with realistic data
- [ ] Verify security authorization paths
- [ ] Run full test suite
- [ ] Check formatting and linting

### ✅ Production Readiness
- [ ] Database migrations tested
- [ ] Monitoring and alerting configured
- [ ] Performance benchmarks established
- [ ] Security review completed
- [ ] Documentation updated
- [ ] Rollback plan prepared

---

## 🎯 Key Success Metrics

1. **Type Safety**: Zero runtime ClassCastException or null pointer errors
2. **Performance**: Sub-100ms response times for CRUD operations
3. **Test Coverage**: >90% line coverage with meaningful assertions
4. **Code Quality**: Zero critical SonarQube issues
5. **Security**: All endpoints properly authorized and validated
6. **Maintainability**: New developers can extend features following patterns

---

Bu qo'llanma senior Scala developerlar uchun professional va scalable backend tizimlar qurishda zarur bo'lgan barcha asosiy narsalarni qamrab oladi. Har bir pattern va practice production-ready kodda sinab ko'rilgan va katta tizimlarda muvaffaqiyat bilan qo'llanilgan.