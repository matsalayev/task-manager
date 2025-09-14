# Projects Management Backend Implementation

## Hozirgi Holat

### ✅ Mavjud Funksiyalar:
- Telegram bot orqali basic project navigation
- File upload S3 integration
- Project domain models (partially)

### ❌ Qo'shilishi Kerak:
- Complete Projects CRUD API
- Phases/Milestones management
- Team assignment to projects
- Project templates
- Project timeline and Gantt charts

## Implementation Tasks

### 1. Project Domain Models
**Priority: 🔴 High**
**Fayl**: `endpoints/00-domain/src/main/scala/tm/domain/projects/`

```scala
case class Project(
  id: ProjectId,
  name: String,
  description: Option[String],
  companyId: CompanyId,
  ownerId: UserId,
  status: ProjectStatus,
  priority: Priority,
  startDate: Option[LocalDate],
  endDate: Option[LocalDate],
  budget: Option[BigDecimal],
  actualCost: Option[BigDecimal],
  progress: Int, // 0-100
  color: Option[String],
  isArchived: Boolean,
  createdAt: Instant,
  updatedAt: Instant
)

sealed trait ProjectStatus
object ProjectStatus {
  case object Planning extends ProjectStatus
  case object Active extends ProjectStatus
  case object OnHold extends ProjectStatus
  case object Completed extends ProjectStatus
  case object Cancelled extends ProjectStatus
}

sealed trait Priority
object Priority {
  case object Low extends Priority
  case object Medium extends Priority
  case object High extends Priority
  case object Critical extends Priority
}

case class ProjectPhase(
  id: ProjectPhaseId,
  projectId: ProjectId,
  name: String,
  description: Option[String],
  order: Int,
  startDate: Option[LocalDate],
  endDate: Option[LocalDate],
  status: PhaseStatus,
  progress: Int,
  createdAt: Instant
)

case class ProjectMember(
  projectId: ProjectId,
  userId: UserId,
  role: ProjectRole,
  assignedAt: Instant
)

sealed trait ProjectRole
object ProjectRole {
  case object Owner extends ProjectRole
  case object Manager extends ProjectRole
  case object Developer extends ProjectRole
  case object Designer extends ProjectRole
  case object Tester extends ProjectRole
  case object Viewer extends ProjectRole
}
```

### 2. Database Schema
**Migration**: `endpoints/01-repos/src/main/resources/db/migration/V004__projects.sql`

```sql
-- Projects table
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'Planning',
    priority VARCHAR(20) NOT NULL DEFAULT 'Medium',
    start_date DATE,
    end_date DATE,
    budget DECIMAL(15,2),
    actual_cost DECIMAL(15,2) DEFAULT 0,
    progress INTEGER DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
    color VARCHAR(7), -- HEX color code
    is_archived BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT valid_dates CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date)
);

-- Project phases
CREATE TABLE project_phases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    phase_order INTEGER NOT NULL,
    start_date DATE,
    end_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'NotStarted',
    progress INTEGER DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
    created_at TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE(project_id, phase_order)
);

-- Project team members
CREATE TABLE project_members (
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'Developer',
    assigned_at TIMESTAMPTZ DEFAULT NOW(),

    PRIMARY KEY (project_id, user_id)
);

-- Project templates
CREATE TABLE project_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    company_id UUID REFERENCES companies(id) ON DELETE CASCADE,
    template_data JSONB NOT NULL,
    is_public BOOLEAN DEFAULT false,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Project files/documents
CREATE TABLE project_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT,
    mime_type VARCHAR(100),
    category VARCHAR(50), -- 'design', 'document', 'code', 'other'
    version INTEGER DEFAULT 1,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_projects_company ON projects(company_id);
CREATE INDEX idx_projects_owner ON projects(owner_id);
CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_project_phases_project ON project_phases(project_id);
CREATE INDEX idx_project_members_project ON project_members(project_id);
CREATE INDEX idx_project_members_user ON project_members(user_id);
CREATE INDEX idx_project_documents_project ON project_documents(project_id);
```

### 3. Project Repository
**Fayl**: `endpoints/01-repos/src/main/scala/tm/repos/ProjectRepo.scala`

```scala
trait ProjectRepo[F[_]] {
  // Basic CRUD
  def create(project: ProjectCreate): F[Project]
  def findById(id: ProjectId): F[Option[Project]]
  def update(id: ProjectId, update: ProjectUpdate): F[Option[Project]]
  def delete(id: ProjectId): F[Boolean]

  // Query operations
  def listByCompany(companyId: CompanyId, filters: ProjectFilters): F[List[Project]]
  def findByOwner(ownerId: UserId): F[List[Project]]
  def searchProjects(query: String, companyId: CompanyId): F[List[Project]]
  def getProjectStats(projectId: ProjectId): F[ProjectStats]

  // Phase operations
  def createPhase(phase: ProjectPhaseCreate): F[ProjectPhase]
  def listPhases(projectId: ProjectId): F[List[ProjectPhase]]
  def updatePhase(phaseId: ProjectPhaseId, update: ProjectPhaseUpdate): F[Option[ProjectPhase]]
  def deletePhase(phaseId: ProjectPhaseId): F[Boolean]
  def reorderPhases(projectId: ProjectId, phaseOrders: List[(ProjectPhaseId, Int)]): F[Unit]

  // Team management
  def addMember(projectId: ProjectId, userId: UserId, role: ProjectRole): F[Unit]
  def removeMember(projectId: ProjectId, userId: UserId): F[Unit]
  def updateMemberRole(projectId: ProjectId, userId: UserId, role: ProjectRole): F[Unit]
  def listMembers(projectId: ProjectId): F[List[ProjectMember]]
  def getUserProjects(userId: UserId): F[List[Project]]

  // Document management
  def addDocument(document: ProjectDocumentCreate): F[ProjectDocument]
  def listDocuments(projectId: ProjectId): F[List[ProjectDocument]]
  def deleteDocument(documentId: ProjectDocumentId): F[Boolean]
}

case class ProjectFilters(
  status: Option[ProjectStatus] = None,
  priority: Option[Priority] = None,
  ownerId: Option[UserId] = None,
  memberId: Option[UserId] = None,
  isArchived: Option[Boolean] = None,
  startDateFrom: Option[LocalDate] = None,
  startDateTo: Option[LocalDate] = None,
  limit: Int = 50,
  offset: Int = 0
)

case class ProjectStats(
  totalTasks: Int,
  completedTasks: Int,
  inProgressTasks: Int,
  totalMembers: Int,
  totalDocuments: Int,
  budgetUtilization: Option[BigDecimal]
)
```

### 4. Project Service
**Fayl**: `endpoints/02-core/src/main/scala/tm/services/ProjectService.scala`

```scala
trait ProjectService[F[_]] {
  // Project management
  def createProject(create: ProjectCreate, ownerId: UserId): F[Project]
  def getProject(id: ProjectId, userId: UserId): F[Option[Project]]
  def updateProject(id: ProjectId, update: ProjectUpdate, userId: UserId): F[Either[ProjectError, Project]]
  def deleteProject(id: ProjectId, userId: UserId): F[Either[ProjectError, Unit]]
  def archiveProject(id: ProjectId, userId: UserId): F[Either[ProjectError, Unit]]

  // Project listing and filtering
  def listUserProjects(userId: UserId, filters: ProjectFilters): F[List[Project]]
  def listCompanyProjects(companyId: CompanyId, userId: UserId, filters: ProjectFilters): F[List[Project]]
  def searchProjects(query: String, userId: UserId): F[List[Project]]

  // Phase management
  def createPhase(projectId: ProjectId, create: ProjectPhaseCreate, userId: UserId): F[Either[ProjectError, ProjectPhase]]
  def updatePhase(phaseId: ProjectPhaseId, update: ProjectPhaseUpdate, userId: UserId): F[Either[ProjectError, ProjectPhase]]
  def deletePhase(phaseId: ProjectPhaseId, userId: UserId): F[Either[ProjectError, Unit]]
  def reorderPhases(projectId: ProjectId, newOrder: List[ProjectPhaseId], userId: UserId): F[Either[ProjectError, Unit]]

  // Team management
  def addTeamMember(projectId: ProjectId, userId: UserId, role: ProjectRole, managerId: UserId): F[Either[ProjectError, Unit]]
  def removeTeamMember(projectId: ProjectId, userId: UserId, managerId: UserId): F[Either[ProjectError, Unit]]
  def updateMemberRole(projectId: ProjectId, userId: UserId, newRole: ProjectRole, managerId: UserId): F[Either[ProjectError, Unit]]
  def getProjectTeam(projectId: ProjectId, userId: UserId): F[List[ProjectMemberWithUser]]

  // Analytics and reporting
  def getProjectDashboard(projectId: ProjectId, userId: UserId): F[Either[ProjectError, ProjectDashboard]]
  def getProjectTimeline(projectId: ProjectId, userId: UserId): F[Either[ProjectError, ProjectTimeline]]
  def calculateProjectProgress(projectId: ProjectId): F[Int]

  // Templates
  def createTemplate(projectId: ProjectId, templateName: String, userId: UserId): F[Either[ProjectError, ProjectTemplate]]
  def createFromTemplate(templateId: ProjectTemplateId, projectName: String, userId: UserId): F[Either[ProjectError, Project]]
  def listTemplates(companyId: CompanyId): F[List[ProjectTemplate]]
}

sealed trait ProjectError
object ProjectError {
  case object ProjectNotFound extends ProjectError
  case object AccessDenied extends ProjectError
  case object InvalidOperation extends ProjectError
  case class ValidationError(message: String) extends ProjectError
}

case class ProjectDashboard(
  project: Project,
  stats: ProjectStats,
  recentActivities: List[ProjectActivity],
  upcomingMilestones: List[ProjectPhase],
  teamMembers: List[ProjectMemberWithUser]
)
```

### 5. API Routes
**Fayl**: `endpoints/03-api/src/main/scala/tm/endpoint/routes/ProjectRoutes.scala`

```scala
object ProjectRoutes {
  def routes[F[_]: Async](
    projectService: ProjectService[F],
    authMiddleware: AuthMiddleware[F]
  ): HttpRoutes[F] = {

    val openRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
      // Public project templates
      case GET -> Root / "templates" / "public" =>
        // List public project templates
    }

    val protectedRoutes: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of[AuthedUser, F] {
      // Project CRUD
      case GET -> Root as user =>
        // List user projects with filters

      case POST -> Root as user =>
        // Create new project

      case GET -> Root / UUIDVar(projectId) as user =>
        // Get project details

      case PUT -> Root / UUIDVar(projectId) as user =>
        // Update project

      case DELETE -> Root / UUIDVar(projectId) as user =>
        // Delete project

      case POST -> Root / UUIDVar(projectId) / "archive" as user =>
        // Archive project

      // Phase management
      case GET -> Root / UUIDVar(projectId) / "phases" as user =>
        // List project phases

      case POST -> Root / UUIDVar(projectId) / "phases" as user =>
        // Create new phase

      case PUT -> Root / UUIDVar(projectId) / "phases" / UUIDVar(phaseId) as user =>
        // Update phase

      case DELETE -> Root / UUIDVar(projectId) / "phases" / UUIDVar(phaseId) as user =>
        // Delete phase

      case POST -> Root / UUIDVar(projectId) / "phases" / "reorder" as user =>
        // Reorder phases

      // Team management
      case GET -> Root / UUIDVar(projectId) / "team" as user =>
        // Get project team members

      case POST -> Root / UUIDVar(projectId) / "team" as user =>
        // Add team member

      case PUT -> Root / UUIDVar(projectId) / "team" / UUIDVar(userId) as user =>
        // Update member role

      case DELETE -> Root / UUIDVar(projectId) / "team" / UUIDVar(userId) as user =>
        // Remove team member

      // Analytics and dashboard
      case GET -> Root / UUIDVar(projectId) / "dashboard" as user =>
        // Get project dashboard data

      case GET -> Root / UUIDVar(projectId) / "timeline" as user =>
        // Get project timeline

      case GET -> Root / UUIDVar(projectId) / "stats" as user =>
        // Get project statistics

      // Templates
      case POST -> Root / UUIDVar(projectId) / "template" as user =>
        // Create template from project

      case POST -> Root / "from-template" / UUIDVar(templateId) as user =>
        // Create project from template
    }

    openRoutes <+> authMiddleware(protectedRoutes)
  }
}
```

### 6. API Documentation

#### GET /api/projects
**Query Parameters:**
- `status`: ProjectStatus filter
- `priority`: Priority filter
- `owner`: Owner user ID
- `member`: Team member user ID
- `archived`: Boolean
- `limit`: Int (default: 50)
- `offset`: Int (default: 0)

**Response:**
```json
{
  "projects": [
    {
      "id": "uuid",
      "name": "Project Name",
      "description": "Project description",
      "status": "Active",
      "priority": "High",
      "progress": 75,
      "startDate": "2024-01-01",
      "endDate": "2024-06-01",
      "owner": {
        "id": "uuid",
        "firstName": "John",
        "lastName": "Doe"
      },
      "teamSize": 5,
      "taskStats": {
        "total": 20,
        "completed": 15,
        "inProgress": 3,
        "todo": 2
      }
    }
  ],
  "total": 25,
  "hasMore": true
}
```

#### POST /api/projects
```json
{
  "name": "New Project",
  "description": "Project description",
  "startDate": "2024-01-01",
  "endDate": "2024-06-01",
  "budget": 50000.00,
  "priority": "High",
  "color": "#3B82F6",
  "phases": [
    {
      "name": "Planning",
      "description": "Initial planning phase",
      "startDate": "2024-01-01",
      "endDate": "2024-01-15"
    }
  ],
  "teamMembers": [
    {
      "userId": "uuid",
      "role": "Developer"
    }
  ]
}
```

#### GET /api/projects/{id}/dashboard
```json
{
  "project": { /* Project object */ },
  "stats": {
    "totalTasks": 20,
    "completedTasks": 15,
    "inProgressTasks": 3,
    "totalMembers": 5,
    "totalDocuments": 12,
    "budgetUtilization": 75.5
  },
  "recentActivities": [
    {
      "type": "TaskCompleted",
      "description": "Task XYZ completed by John Doe",
      "timestamp": "2024-01-01T12:00:00Z",
      "user": { /* User object */ }
    }
  ],
  "upcomingMilestones": [
    {
      "id": "uuid",
      "name": "Phase 2 Completion",
      "endDate": "2024-02-01",
      "progress": 60
    }
  ]
}
```

## Testing Strategy

1. **Unit Tests**: Service va repository layer testlari
2. **Integration Tests**: API endpoint testlari
3. **Performance Tests**: Katta loyihalar ro'yxati uchun
4. **Security Tests**: Authorization va data access testlar

## Estimated Time: 2-3 hafta