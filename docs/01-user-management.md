# User Management Backend Implementation

## Hozirgi Holat

### ✅ Mavjud Funksiyalar:
- JWT authentication (login/logout/refresh)
- User domain models (AuthedUser, Credentials)
- Password hashing (SCrypt)
- Role-based authorization (Director, Manager, Employee)
- Redis session management

### ❌ Qo'shilishi Kerak:
- User registration API
- Profile management
- User CRUD operations
- Permission system
- Multi-factor authentication

## Implementation Tasks

### 1. User Registration API
**Priority: 🔴 High**
**Fayl**: `endpoints/03-api/src/main/scala/tm/endpoint/routes/UserRoutes.scala`

```scala
// Kerakli endpoints:
POST /users/register       // Yangi foydalanuvchi ro'yxatga olish
POST /users/invite         // Foydalanuvchini taklif qilish
POST /users/verify-email   // Email tasdiqlash
POST /users/verify-phone   // Telefon tasdiqlash
```

**Domain Models** (`endpoints/00-domain/src/main/scala/tm/domain/users/`):
```scala
case class UserRegistration(
  phone: Phone,
  email: Email,
  firstName: String,
  lastName: String,
  password: String,
  companyInviteToken: Option[String]
)

case class UserProfile(
  id: UserId,
  phone: Phone,
  email: Email,
  firstName: String,
  lastName: String,
  avatar: Option[String],
  role: UserRole,
  isActive: Boolean,
  createdAt: Instant,
  updatedAt: Instant
)
```

### 2. User CRUD Operations
**Priority: 🔴 High**
**Service**: `endpoints/02-core/src/main/scala/tm/services/UserService.scala`

```scala
trait UserService[F[_]] {
  def createUser(registration: UserRegistration): F[User]
  def getUserById(id: UserId): F[Option[User]]
  def getUserByPhone(phone: Phone): F[Option[User]]
  def getUserByEmail(email: Email): F[Option[User]]
  def updateProfile(id: UserId, update: UserProfileUpdate): F[User]
  def deactivateUser(id: UserId): F[Unit]
  def listUsers(companyId: CompanyId, pagination: Pagination): F[List[User]]
  def searchUsers(query: String, companyId: CompanyId): F[List[User]]
}
```

### 3. Database Schema
**Migration**: `endpoints/01-repos/src/main/resources/db/migration/V003__users_extended.sql`

```sql
-- Users jadvalini kengaytirish
ALTER TABLE users ADD COLUMN email VARCHAR(255) UNIQUE;
ALTER TABLE users ADD COLUMN first_name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN last_name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN avatar_url TEXT;
ALTER TABLE users ADD COLUMN is_active BOOLEAN DEFAULT true;
ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT false;
ALTER TABLE users ADD COLUMN phone_verified BOOLEAN DEFAULT false;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;

-- User preferences
CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    language VARCHAR(10) DEFAULT 'uz',
    timezone VARCHAR(50) DEFAULT 'Asia/Tashkent',
    theme VARCHAR(20) DEFAULT 'light',
    notifications_enabled BOOLEAN DEFAULT true,
    email_notifications BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Email verification tokens
CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### 4. User Repository
**Fayl**: `endpoints/01-repos/src/main/scala/tm/repos/UserRepo.scala`

Kengaytirish kerak:
```scala
trait UserRepo[F[_]] {
  // Mavjud metodlar + qo'shimcha:
  def create(user: UserCreate): F[User]
  def update(id: UserId, update: UserUpdate): F[Option[User]]
  def findByEmail(email: Email): F[Option[User]]
  def searchByName(name: String, companyId: CompanyId): F[List[User]]
  def listByCompany(companyId: CompanyId, limit: Int, offset: Int): F[List[User]]
  def updateLastLogin(id: UserId): F[Unit]
}
```

### 5. Profile Management
**Priority: 🟡 Medium**

**API Endpoints**:
```scala
GET    /users/me              // Joriy foydalanuvchi profili
PUT    /users/me              // Profilni yangilash
PUT    /users/me/avatar       // Avatar yuklash
PUT    /users/me/password     // Parolni o'zgartirish
GET    /users/me/preferences  // Sozlamalar
PUT    /users/me/preferences  // Sozlamalarni yangilash
```

### 6. Multi-Factor Authentication
**Priority: 🟡 Medium**

**Domain Models**:
```scala
case class MFASetup(
  userId: UserId,
  method: MFAMethod, // SMS, EMAIL, TOTP
  secret: String,
  isEnabled: Boolean
)

sealed trait MFAMethod
object MFAMethod {
  case object SMS extends MFAMethod
  case object Email extends MFAMethod
  case object TOTP extends MFAMethod
}
```

**API Endpoints**:
```scala
POST /auth/mfa/setup       // MFA sozlash
POST /auth/mfa/verify      // MFA tasdiqlash
POST /auth/mfa/disable     // MFA o'chirish
GET  /auth/mfa/qr          // TOTP uchun QR kod
```

### 7. Permission System
**Priority: 🟡 Medium**

```scala
sealed trait Permission
object Permission {
  // Company permissions
  case object ManageCompany extends Permission
  case object ViewCompany extends Permission

  // User permissions
  case object ManageUsers extends Permission
  case object ViewUsers extends Permission
  case object InviteUsers extends Permission

  // Project permissions
  case object CreateProjects extends Permission
  case object ManageAllProjects extends Permission
  case object ViewAllProjects extends Permission

  // Task permissions
  case object CreateTasks extends Permission
  case object ManageAllTasks extends Permission
  case object ViewAllTasks extends Permission
}

case class RolePermissions(
  role: UserRole,
  permissions: Set[Permission]
)
```

## API Documentation

### POST /users/register
```json
{
  "phone": "+998901234567",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "password": "SecurePassword123!",
  "companyInviteToken": "optional_invite_token"
}
```

**Response**:
```json
{
  "id": "uuid",
  "phone": "+998901234567",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "role": "Employee",
  "isActive": true,
  "emailVerified": false,
  "phoneVerified": true,
  "createdAt": "2024-01-01T12:00:00Z"
}
```

### GET /users/me
**Headers**: `Authorization: Bearer <jwt_token>`

**Response**:
```json
{
  "id": "uuid",
  "phone": "+998901234567",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "avatar": "https://s3.amazonaws.com/bucket/avatar.jpg",
  "role": "Employee",
  "company": {
    "id": "uuid",
    "name": "Company Name"
  },
  "preferences": {
    "language": "uz",
    "timezone": "Asia/Tashkent",
    "theme": "light",
    "notificationsEnabled": true
  }
}
```

## Testing Tasks

1. **Unit Tests**: UserService metodlari uchun testlar
2. **Integration Tests**: API endpoints uchun testlar
3. **Security Tests**: Authentication va authorization testlar
4. **Performance Tests**: Katta foydalanuvchilar ro'yxati uchun

## Migration Plan

1. **V003**: Users jadvalini kengaytirish
2. **V004**: User preferences jadvali
3. **V005**: MFA tables
4. **V006**: Permissions system

## Estimated Time: 1-2 hafta