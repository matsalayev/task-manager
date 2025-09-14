# Tasks Management Backend Implementation

## Hozirgi Holat

### ✅ Mavjud Funksiyalar:
- Telegram bot orqali basic task creation
- Task status enumeration (ToDo, InProgress, InReview, Testing, Done)
- Basic task domain models (partially implemented)

### ❌ Qo'shilishi Kerak:
- Complete Task CRUD API
- Kanban board functionality
- Task assignments and dependencies
- Task comments and attachments
- Task time tracking integration

## Implementation Tasks

### 1. Task Domain Models
**Priority: 🔴 High**
**Fayl**: `endpoints/00-domain/src/main/scala/tm/domain/tasks/`

```scala
case class Task(
  id: TaskId,
  title: String,
  description: Option[String],
  projectId: ProjectId,
  phaseId: Option[ProjectPhaseId],
  assigneeId: Option[UserId],
  reporterId: UserId,
  status: TaskStatus,
  priority: Priority,
  estimatedHours: Option[Int],
  actualHours: Option[Int],
  startDate: Option[LocalDateTime],
  dueDate: Option[LocalDateTime],
  completedDate: Option[LocalDateTime],
  tags: List[String],
  position: Int, // For Kanban ordering
  parentTaskId: Option[TaskId], // For subtasks
  isArchived: Boolean,
  createdAt: Instant,
  updatedAt: Instant
)

sealed trait TaskStatus
object TaskStatus {
  case object Todo extends TaskStatus
  case object InProgress extends TaskStatus
  case object InReview extends TaskStatus
  case object Testing extends TaskStatus
  case object Done extends TaskStatus
  case object Cancelled extends TaskStatus
}

case class TaskComment(
  id: TaskCommentId,
  taskId: TaskId,
  authorId: UserId,
  content: String,
  parentCommentId: Option[TaskCommentId],
  isEdited: Boolean,
  createdAt: Instant,
  updatedAt: Instant
)

case class TaskAttachment(
  id: TaskAttachmentId,
  taskId: TaskId,
  fileName: String,
  filePath: String,
  fileSize: Long,
  mimeType: String,
  uploadedBy: UserId,
  uploadedAt: Instant
)

case class TaskDependency(
  id: TaskDependencyId,
  dependentTaskId: TaskId,
  dependencyTaskId: TaskId,
  dependencyType: DependencyType,
  createdAt: Instant
)

sealed trait DependencyType
object DependencyType {
  case object FinishToStart extends DependencyType // Default: Task B cannot start until Task A finishes
  case object StartToStart extends DependencyType   // Task B cannot start until Task A starts
  case object FinishToFinish extends DependencyType // Task B cannot finish until Task A finishes
  case object StartToFinish extends DependencyType  // Task B cannot finish until Task A starts
}

case class TaskTimeEntry(
  id: TaskTimeEntryId,
  taskId: TaskId,
  userId: UserId,
  startTime: LocalDateTime,
  endTime: Option[LocalDateTime],
  description: Option[String],
  isRunning: Boolean,
  createdAt: Instant
)
```

### 2. Database Schema
**Migration**: `endpoints/01-repos/src/main/resources/db/migration/V005__tasks.sql`

```sql
-- Tasks table
CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    phase_id UUID REFERENCES project_phases(id) ON DELETE SET NULL,
    assignee_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reporter_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'Todo',
    priority VARCHAR(20) NOT NULL DEFAULT 'Medium',
    estimated_hours INTEGER,
    actual_hours INTEGER,
    start_date TIMESTAMPTZ,
    due_date TIMESTAMPTZ,
    completed_date TIMESTAMPTZ,
    tags TEXT[], -- PostgreSQL array
    position INTEGER NOT NULL DEFAULT 0,
    parent_task_id UUID REFERENCES tasks(id) ON DELETE CASCADE,
    is_archived BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT valid_hours CHECK (estimated_hours IS NULL OR estimated_hours > 0),
    CONSTRAINT valid_actual_hours CHECK (actual_hours IS NULL OR actual_hours >= 0),
    CONSTRAINT valid_dates CHECK (start_date IS NULL OR due_date IS NULL OR start_date <= due_date)
);

-- Task comments
CREATE TABLE task_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    parent_comment_id UUID REFERENCES task_comments(id) ON DELETE CASCADE,
    is_edited BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Task attachments
CREATE TABLE task_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    uploaded_by UUID NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMPTZ DEFAULT NOW()
);

-- Task dependencies
CREATE TABLE task_dependencies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dependent_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    dependency_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    dependency_type VARCHAR(20) NOT NULL DEFAULT 'FinishToStart',
    created_at TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE(dependent_task_id, dependency_task_id),
    CONSTRAINT no_self_dependency CHECK (dependent_task_id != dependency_task_id)
);

-- Task time entries
CREATE TABLE task_time_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    description TEXT,
    is_running BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT valid_time_entry CHECK (end_time IS NULL OR start_time <= end_time)
);

-- Task watchers (users who want to be notified about task changes)
CREATE TABLE task_watchers (
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    watched_at TIMESTAMPTZ DEFAULT NOW(),

    PRIMARY KEY (task_id, user_id)
);

-- Task labels/tags (if we want more structured tagging)
CREATE TABLE task_labels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    color VARCHAR(7), -- HEX color
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE(name, project_id)
);

CREATE TABLE task_label_assignments (
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    label_id UUID NOT NULL REFERENCES task_labels(id) ON DELETE CASCADE,

    PRIMARY KEY (task_id, label_id)
);

-- Indexes for performance
CREATE INDEX idx_tasks_project ON tasks(project_id);
CREATE INDEX idx_tasks_assignee ON tasks(assignee_id);
CREATE INDEX idx_tasks_reporter ON tasks(reporter_id);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);
CREATE INDEX idx_tasks_position ON tasks(project_id, status, position);
CREATE INDEX idx_task_comments_task ON task_comments(task_id);
CREATE INDEX idx_task_time_entries_task ON task_time_entries(task_id);
CREATE INDEX idx_task_time_entries_user ON task_time_entries(user_id);
CREATE INDEX idx_task_time_running ON task_time_entries(user_id, is_running) WHERE is_running = true;

-- Full-text search index for task content
CREATE INDEX idx_tasks_search ON tasks USING GIN(to_tsvector('english', title || ' ' || COALESCE(description, '')));
```

### 3. Task Repository
**Fayl**: `endpoints/01-repos/src/main/scala/tm/repos/TaskRepo.scala`

```scala
trait TaskRepo[F[_]] {
  // Basic CRUD
  def create(task: TaskCreate): F[Task]
  def findById(id: TaskId): F[Option[Task]]
  def update(id: TaskId, update: TaskUpdate): F[Option[Task]]
  def delete(id: TaskId): F[Boolean]

  // Kanban operations
  def listByProject(projectId: ProjectId, filters: TaskFilters): F[List[Task]]
  def listByStatus(projectId: ProjectId, status: TaskStatus): F[List[Task]]
  def updatePosition(taskId: TaskId, newPosition: Int, newStatus: Option[TaskStatus]): F[Option[Task]]
  def reorderTasks(projectId: ProjectId, status: TaskStatus, taskPositions: List[(TaskId, Int)]): F[Unit]

  // Query operations
  def findByAssignee(assigneeId: UserId, filters: TaskFilters): F[List[Task]]
  def findByReporter(reporterId: UserId, filters: TaskFilters): F[List[Task]]
  def searchTasks(query: String, projectId: Option[ProjectId], userId: UserId): F[List[Task]]
  def findOverdueTasks(projectId: Option[ProjectId]): F[List[Task]]

  // Time tracking
  def startTimeEntry(taskId: TaskId, userId: UserId, description: Option[String]): F[TaskTimeEntry]
  def stopTimeEntry(entryId: TaskTimeEntryId): F[Option[TaskTimeEntry]]
  def listTimeEntries(taskId: TaskId): F[List[TaskTimeEntry]]
  def getUserActiveTimeEntry(userId: UserId): F[Option[TaskTimeEntry]]

  // Comments
  def addComment(comment: TaskCommentCreate): F[TaskComment]
  def listComments(taskId: TaskId): F[List[TaskComment]]
  def updateComment(commentId: TaskCommentId, content: String): F[Option[TaskComment]]
  def deleteComment(commentId: TaskCommentId): F[Boolean]

  // Attachments
  def addAttachment(attachment: TaskAttachmentCreate): F[TaskAttachment]
  def listAttachments(taskId: TaskId): F[List[TaskAttachment]]
  def deleteAttachment(attachmentId: TaskAttachmentId): F[Boolean]

  // Dependencies
  def addDependency(dependency: TaskDependencyCreate): F[TaskDependency]
  def listDependencies(taskId: TaskId): F[List[TaskDependency]]
  def removeDependency(dependencyId: TaskDependencyId): F[Boolean]
  def validateDependency(dependentId: TaskId, dependencyId: TaskId): F[Boolean]

  // Subtasks
  def listSubtasks(parentTaskId: TaskId): F[List[Task]]
  def moveSubtask(subtaskId: TaskId, newParentId: Option[TaskId]): F[Option[Task]]

  // Watchers
  def addWatcher(taskId: TaskId, userId: UserId): F[Unit]
  def removeWatcher(taskId: TaskId, userId: UserId): F[Unit]
  def listWatchers(taskId: TaskId): F[List[UserId]]

  // Statistics
  def getTaskStats(projectId: ProjectId): F[TaskStats]
  def getUserTaskStats(userId: UserId, projectId: Option[ProjectId]): F[UserTaskStats]
}

case class TaskFilters(
  status: Option[TaskStatus] = None,
  assigneeId: Option[UserId] = None,
  reporterId: Option[UserId] = None,
  priority: Option[Priority] = None,
  phaseId: Option[ProjectPhaseId] = None,
  tags: List[String] = Nil,
  dueDateFrom: Option[LocalDateTime] = None,
  dueDateTo: Option[LocalDateTime] = None,
  isOverdue: Option[Boolean] = None,
  hasSubtasks: Option[Boolean] = None,
  limit: Int = 100,
  offset: Int = 0
)

case class TaskStats(
  totalTasks: Int,
  tasksByStatus: Map[TaskStatus, Int],
  tasksByPriority: Map[Priority, Int],
  overdueTasks: Int,
  completedThisWeek: Int,
  averageCompletionTime: Option[Double] // in hours
)
```

### 4. Task Service
**Fayl**: `endpoints/02-core/src/main/scala/tm/services/TaskService.scala`

```scala
trait TaskService[F[_]] {
  // Task management
  def createTask(create: TaskCreate, userId: UserId): F[Either[TaskError, Task]]
  def getTask(id: TaskId, userId: UserId): F[Either[TaskError, TaskWithDetails]]
  def updateTask(id: TaskId, update: TaskUpdate, userId: UserId): F[Either[TaskError, Task]]
  def deleteTask(id: TaskId, userId: UserId): F[Either[TaskError, Unit]]
  def archiveTask(id: TaskId, userId: UserId): F[Either[TaskError, Unit]]

  // Kanban operations
  def getKanbanBoard(projectId: ProjectId, userId: UserId): F[Either[TaskError, KanbanBoard]]
  def moveTask(taskId: TaskId, newStatus: TaskStatus, newPosition: Int, userId: UserId): F[Either[TaskError, Task]]
  def bulkMoveTask(moves: List[TaskMove], userId: UserId): F[Either[TaskError, Unit]]

  // Assignment
  def assignTask(taskId: TaskId, assigneeId: Option[UserId], assignerId: UserId): F[Either[TaskError, Task]]
  def bulkAssignTasks(taskIds: List[TaskId], assigneeId: Option[UserId], assignerId: UserId): F[Either[TaskError, Unit]]

  // Time tracking
  def startTimer(taskId: TaskId, userId: UserId, description: Option[String]): F[Either[TaskError, TaskTimeEntry]]
  def stopTimer(userId: UserId): F[Either[TaskError, TaskTimeEntry]]
  def pauseTimer(userId: UserId): F[Either[TaskError, Unit]]
  def getActiveTimer(userId: UserId): F[Option[TaskTimeEntry]]
  def logTime(taskId: TaskId, userId: UserId, hours: Double, description: Option[String]): F[Either[TaskError, TaskTimeEntry]]

  // Comments
  def addComment(taskId: TaskId, content: String, userId: UserId): F[Either[TaskError, TaskComment]]
  def updateComment(commentId: TaskCommentId, content: String, userId: UserId): F[Either[TaskError, TaskComment]]
  def deleteComment(commentId: TaskCommentId, userId: UserId): F[Either[TaskError, Unit]]

  // Attachments
  def addAttachment(taskId: TaskId, file: FileUpload, userId: UserId): F[Either[TaskError, TaskAttachment]]
  def deleteAttachment(attachmentId: TaskAttachmentId, userId: UserId): F[Either[TaskError, Unit]]

  // Dependencies
  def addDependency(dependentId: TaskId, dependencyId: TaskId, depType: DependencyType, userId: UserId): F[Either[TaskError, Unit]]
  def removeDependency(dependencyId: TaskDependencyId, userId: UserId): F[Either[TaskError, Unit]]
  def validateTaskTransition(taskId: TaskId, newStatus: TaskStatus): F[Either[TaskError, Unit]]

  // Search and filtering
  def searchTasks(query: String, projectId: Option[ProjectId], userId: UserId): F[List[Task]]
  def getUserTasks(userId: UserId, filters: TaskFilters): F[List[Task]]
  def getProjectTasks(projectId: ProjectId, userId: UserId, filters: TaskFilters): F[Either[TaskError, List[Task]]]

  // Analytics
  def getTaskAnalytics(projectId: ProjectId, userId: UserId): F[Either[TaskError, TaskAnalytics]]
  def getUserProductivity(userId: UserId, dateRange: DateRange): F[UserProductivityReport]
}

sealed trait TaskError
object TaskError {
  case object TaskNotFound extends TaskError
  case object ProjectNotFound extends TaskError
  case object AccessDenied extends TaskError
  case object InvalidTransition extends TaskError
  case object CircularDependency extends TaskError
  case object DependencyNotSatisfied extends TaskError
  case class ValidationError(message: String) extends TaskError
}

case class TaskMove(
  taskId: TaskId,
  newStatus: TaskStatus,
  newPosition: Int
)

case class KanbanBoard(
  projectId: ProjectId,
  columns: List[KanbanColumn],
  swimlanes: Option[List[KanbanSwimlane]] = None
)

case class KanbanColumn(
  status: TaskStatus,
  name: String,
  tasks: List[Task],
  wipLimit: Option[Int] = None
)

case class TaskWithDetails(
  task: Task,
  assignee: Option[User],
  reporter: User,
  comments: List[TaskCommentWithAuthor],
  attachments: List[TaskAttachment],
  timeEntries: List[TaskTimeEntry],
  dependencies: List[TaskDependency],
  subtasks: List[Task],
  watchers: List[User]
)
```

### 5. API Routes
**Fayl**: `endpoints/03-api/src/main/scala/tm/endpoint/routes/TaskRoutes.scala`

```scala
object TaskRoutes {
  def routes[F[_]: Async](
    taskService: TaskService[F],
    authMiddleware: AuthMiddleware[F]
  ): HttpRoutes[F] = {

    val protectedRoutes: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of[AuthedUser, F] {
      // Task CRUD
      case GET -> Root as user =>
        // List user tasks with filters

      case POST -> Root as user =>
        // Create new task

      case GET -> Root / UUIDVar(taskId) as user =>
        // Get task details

      case PUT -> Root / UUIDVar(taskId) as user =>
        // Update task

      case DELETE -> Root / UUIDVar(taskId) as user =>
        // Delete task

      case POST -> Root / UUIDVar(taskId) / "archive" as user =>
        // Archive task

      // Kanban operations
      case GET -> Root / "kanban" / UUIDVar(projectId) as user =>
        // Get Kanban board for project

      case POST -> Root / UUIDVar(taskId) / "move" as user =>
        // Move task to different status/position

      case POST -> Root / "bulk-move" as user =>
        // Bulk move tasks

      // Assignment
      case POST -> Root / UUIDVar(taskId) / "assign" as user =>
        // Assign task to user

      case POST -> Root / "bulk-assign" as user =>
        // Bulk assign tasks

      // Time tracking
      case POST -> Root / UUIDVar(taskId) / "time" / "start" as user =>
        // Start timer for task

      case POST -> Root / "time" / "stop" as user =>
        // Stop active timer

      case GET -> Root / "time" / "active" as user =>
        // Get active timer

      case POST -> Root / UUIDVar(taskId) / "time" / "log" as user =>
        // Log time manually

      case GET -> Root / UUIDVar(taskId) / "time" as user =>
        // Get time entries for task

      // Comments
      case GET -> Root / UUIDVar(taskId) / "comments" as user =>
        // Get task comments

      case POST -> Root / UUIDVar(taskId) / "comments" as user =>
        // Add comment

      case PUT -> Root / "comments" / UUIDVar(commentId) as user =>
        // Update comment

      case DELETE -> Root / "comments" / UUIDVar(commentId) as user =>
        // Delete comment

      // Attachments
      case POST -> Root / UUIDVar(taskId) / "attachments" as user =>
        // Upload attachment

      case GET -> Root / UUIDVar(taskId) / "attachments" as user =>
        // List attachments

      case DELETE -> Root / "attachments" / UUIDVar(attachmentId) as user =>
        // Delete attachment

      // Dependencies
      case GET -> Root / UUIDVar(taskId) / "dependencies" as user =>
        // Get task dependencies

      case POST -> Root / UUIDVar(taskId) / "dependencies" as user =>
        // Add dependency

      case DELETE -> Root / "dependencies" / UUIDVar(dependencyId) as user =>
        // Remove dependency

      // Search
      case GET -> Root / "search" :? QueryParam(query) +& ProjectIdQueryParam(projectId) as user =>
        // Search tasks

      // Analytics
      case GET -> Root / "analytics" / UUIDVar(projectId) as user =>
        // Get task analytics for project
    }

    authMiddleware(protectedRoutes)
  }
}
```

### 6. API Documentation

#### GET /api/tasks/kanban/{projectId}
```json
{
  "projectId": "uuid",
  "columns": [
    {
      "status": "Todo",
      "name": "To Do",
      "wipLimit": null,
      "tasks": [
        {
          "id": "uuid",
          "title": "Task title",
          "description": "Task description",
          "assignee": {
            "id": "uuid",
            "firstName": "John",
            "lastName": "Doe",
            "avatar": "url"
          },
          "priority": "High",
          "estimatedHours": 8,
          "tags": ["frontend", "urgent"],
          "dueDate": "2024-01-15T10:00:00Z",
          "position": 0
        }
      ]
    }
  ]
}
```

#### POST /api/tasks
```json
{
  "title": "New Task",
  "description": "Task description",
  "projectId": "uuid",
  "phaseId": "uuid",
  "assigneeId": "uuid",
  "priority": "High",
  "estimatedHours": 8,
  "startDate": "2024-01-01T09:00:00Z",
  "dueDate": "2024-01-05T17:00:00Z",
  "tags": ["frontend", "react"],
  "dependencies": [
    {
      "dependencyTaskId": "uuid",
      "type": "FinishToStart"
    }
  ]
}
```

#### POST /api/tasks/{id}/move
```json
{
  "newStatus": "InProgress",
  "newPosition": 2
}
```

#### POST /api/tasks/{id}/time/start
```json
{
  "description": "Working on login component"
}
```

## Testing Strategy

1. **Unit Tests**: Service va repository metodlari
2. **Integration Tests**: API endpoints va Kanban operations
3. **Performance Tests**: Katta task lists va real-time updates
4. **Concurrency Tests**: Simultaneous time tracking va task movements

## Estimated Time: 2-3 hafta