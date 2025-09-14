# Time Tracking Backend Implementation

## Hozirgi Holat

### ✅ Mavjud Funksiyalar:
- Basic time entry models (TaskTimeEntry)
- Task-level time logging structure

### ❌ Qo'shilishi Kerak:
- Complete time tracking API
- Dashboard time analytics
- Work mode tracking (office/remote)
- Break time management
- Automatic time detection
- Time reports and analytics

## Implementation Tasks

### 1. Time Tracking Domain Models
**Priority: 🔴 High**
**Fayl**: `endpoints/00-domain/src/main/scala/tm/domain/time/`

```scala
case class WorkSession(
  id: WorkSessionId,
  userId: UserId,
  startTime: LocalDateTime,
  endTime: Option[LocalDateTime],
  workMode: WorkMode,
  isRunning: Boolean,
  totalMinutes: Int,
  breakMinutes: Int,
  productiveMinutes: Int,
  description: Option[String],
  location: Option[String], // For office work
  createdAt: Instant
)

sealed trait WorkMode
object WorkMode {
  case object Office extends WorkMode
  case object Remote extends WorkMode
  case object Hybrid extends WorkMode
}

case class TimeEntry(
  id: TimeEntryId,
  userId: UserId,
  taskId: Option[TaskId],
  workSessionId: Option[WorkSessionId],
  startTime: LocalDateTime,
  endTime: Option[LocalDateTime],
  duration: Option[Int], // minutes
  description: String,
  isRunning: Boolean,
  isBreak: Boolean,
  breakReason: Option[BreakReason],
  isManual: Boolean, // manually logged vs timer
  createdAt: Instant,
  updatedAt: Instant
)

sealed trait BreakReason
object BreakReason {
  case object Lunch extends BreakReason
  case object Coffee extends BreakReason
  case object Meeting extends BreakReason
  case object Personal extends BreakReason
  case object Toilet extends BreakReason
  case object Other extends BreakReason
}

case class DailyTimeReport(
  userId: UserId,
  date: LocalDate,
  totalWorkedMinutes: Int,
  productiveMinutes: Int,
  breakMinutes: Int,
  tasksWorked: Int,
  workMode: Option[WorkMode],
  startTime: Option[LocalDateTime],
  endTime: Option[LocalDateTime],
  overtime: Int, // minutes over standard work day
  isHoliday: Boolean
)

case class WeeklyTimeReport(
  userId: UserId,
  weekStart: LocalDate,
  totalWorkedHours: Double,
  productiveHours: Double,
  overtimeHours: Double,
  workDays: Int,
  averageDailyHours: Double,
  dailyReports: List[DailyTimeReport]
)

case class TimeTargets(
  userId: UserId,
  dailyTargetMinutes: Int, // 8 hours = 480 minutes
  weeklyTargetMinutes: Int, // 40 hours = 2400 minutes
  monthlyTargetMinutes: Int,
  maxOvertimeMinutes: Int,
  requiredBreakMinutes: Int, // per day
  createdAt: Instant,
  updatedAt: Instant
)

case class ActivityLog(
  id: ActivityLogId,
  userId: UserId,
  activityType: ActivityType,
  timestamp: LocalDateTime,
  metadata: Map[String, String], // JSON-like metadata
  createdAt: Instant
)

sealed trait ActivityType
object ActivityType {
  case object SessionStart extends ActivityType
  case object SessionEnd extends ActivityType
  case object BreakStart extends ActivityType
  case object BreakEnd extends ActivityType
  case object TaskStart extends ActivityType
  case object TaskEnd extends ActivityType
  case object ModeChange extends ActivityType
}
```

### 2. Database Schema
**Migration**: `endpoints/01-repos/src/main/resources/db/migration/V006__time_tracking.sql`

```sql
-- Work sessions (main work day tracking)
CREATE TABLE work_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    work_mode VARCHAR(20) NOT NULL DEFAULT 'Remote',
    is_running BOOLEAN DEFAULT false,
    total_minutes INTEGER DEFAULT 0,
    break_minutes INTEGER DEFAULT 0,
    productive_minutes INTEGER DEFAULT 0,
    description TEXT,
    location VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT valid_session_time CHECK (end_time IS NULL OR start_time <= end_time),
    CONSTRAINT valid_minutes CHECK (total_minutes >= 0 AND break_minutes >= 0 AND productive_minutes >= 0)
);

-- Time entries (granular time tracking)
CREATE TABLE time_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
    work_session_id UUID REFERENCES work_sessions(id) ON DELETE SET NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    duration_minutes INTEGER,
    description TEXT NOT NULL,
    is_running BOOLEAN DEFAULT false,
    is_break BOOLEAN DEFAULT false,
    break_reason VARCHAR(20),
    is_manual BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT valid_entry_time CHECK (end_time IS NULL OR start_time <= end_time),
    CONSTRAINT break_reason_required CHECK (is_break = false OR break_reason IS NOT NULL)
);

-- Daily time reports (aggregated daily stats)
CREATE TABLE daily_time_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    report_date DATE NOT NULL,
    total_worked_minutes INTEGER NOT NULL DEFAULT 0,
    productive_minutes INTEGER NOT NULL DEFAULT 0,
    break_minutes INTEGER NOT NULL DEFAULT 0,
    tasks_worked INTEGER NOT NULL DEFAULT 0,
    work_mode VARCHAR(20),
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    overtime_minutes INTEGER DEFAULT 0,
    is_holiday BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE(user_id, report_date)
);

-- Time targets (user goals and limits)
CREATE TABLE time_targets (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    daily_target_minutes INTEGER DEFAULT 480, -- 8 hours
    weekly_target_minutes INTEGER DEFAULT 2400, -- 40 hours
    monthly_target_minutes INTEGER DEFAULT 10400, -- ~173 hours (4.3 weeks)
    max_overtime_minutes INTEGER DEFAULT 120, -- 2 hours
    required_break_minutes INTEGER DEFAULT 30, -- 30 min break per day
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Activity log (detailed user activity tracking)
CREATE TABLE activity_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_type VARCHAR(20) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Time approval workflow (for companies that require time approval)
CREATE TABLE time_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    approver_id UUID NOT NULL REFERENCES users(id),
    report_date DATE NOT NULL,
    submitted_minutes INTEGER NOT NULL,
    approved_minutes INTEGER,
    status VARCHAR(20) DEFAULT 'Pending', -- Pending, Approved, Rejected
    comments TEXT,
    submitted_at TIMESTAMPTZ DEFAULT NOW(),
    approved_at TIMESTAMPTZ,

    UNIQUE(user_id, report_date)
);

-- Indexes for performance
CREATE INDEX idx_work_sessions_user_date ON work_sessions(user_id, DATE(start_time));
CREATE INDEX idx_work_sessions_running ON work_sessions(user_id, is_running) WHERE is_running = true;
CREATE INDEX idx_time_entries_user_date ON time_entries(user_id, DATE(start_time));
CREATE INDEX idx_time_entries_task ON time_entries(task_id) WHERE task_id IS NOT NULL;
CREATE INDEX idx_time_entries_running ON time_entries(user_id, is_running) WHERE is_running = true;
CREATE INDEX idx_daily_reports_user_date ON daily_time_reports(user_id, report_date);
CREATE INDEX idx_activity_logs_user_time ON activity_logs(user_id, timestamp);

-- Constraints to prevent multiple running sessions
CREATE UNIQUE INDEX idx_one_running_session_per_user
ON work_sessions(user_id) WHERE is_running = true;

CREATE UNIQUE INDEX idx_one_running_entry_per_user
ON time_entries(user_id) WHERE is_running = true;
```

### 3. Time Tracking Repository
**Fayl**: `endpoints/01-repos/src/main/scala/tm/repos/TimeTrackingRepo.scala`

```scala
trait TimeTrackingRepo[F[_]] {
  // Work sessions
  def startWorkSession(userId: UserId, workMode: WorkMode, location: Option[String]): F[WorkSession]
  def endWorkSession(sessionId: WorkSessionId): F[Option[WorkSession]]
  def getCurrentWorkSession(userId: UserId): F[Option[WorkSession]]
  def updateWorkSession(sessionId: WorkSessionId, update: WorkSessionUpdate): F[Option[WorkSession]]

  // Time entries
  def startTimeEntry(create: TimeEntryCreate): F[TimeEntry]
  def endTimeEntry(entryId: TimeEntryId): F[Option[TimeEntry]]
  def logManualTime(entry: ManualTimeEntry): F[TimeEntry]
  def getCurrentTimeEntry(userId: UserId): F[Option[TimeEntry]]
  def listTimeEntries(userId: UserId, dateRange: DateRange): F[List[TimeEntry]]
  def updateTimeEntry(entryId: TimeEntryId, update: TimeEntryUpdate): F[Option[TimeEntry]]
  def deleteTimeEntry(entryId: TimeEntryId): F[Boolean]

  // Breaks
  def startBreak(userId: UserId, reason: BreakReason, description: Option[String]): F[TimeEntry]
  def endBreak(userId: UserId): F[Option[TimeEntry]]
  def getCurrentBreak(userId: UserId): F[Option[TimeEntry]]

  // Daily reports
  def generateDailyReport(userId: UserId, date: LocalDate): F[DailyTimeReport]
  def getDailyReport(userId: UserId, date: LocalDate): F[Option[DailyTimeReport]]
  def updateDailyReport(userId: UserId, date: LocalDate): F[Option[DailyTimeReport]]
  def listDailyReports(userId: UserId, dateRange: DateRange): F[List[DailyTimeReport]]

  // Weekly/Monthly reports
  def generateWeeklyReport(userId: UserId, weekStart: LocalDate): F[WeeklyTimeReport]
  def generateMonthlyReport(userId: UserId, month: YearMonth): F[MonthlyTimeReport]

  // Time targets
  def getTimeTargets(userId: UserId): F[Option[TimeTargets]]
  def updateTimeTargets(userId: UserId, targets: TimeTargetsUpdate): F[TimeTargets]

  // Activity logging
  def logActivity(activity: ActivityLogCreate): F[ActivityLog]
  def getActivityLog(userId: UserId, dateRange: DateRange): F[List[ActivityLog]]

  // Analytics
  def getUserTimeStats(userId: UserId, dateRange: DateRange): F[UserTimeStats]
  def getTeamTimeStats(userIds: List[UserId], dateRange: DateRange): F[TeamTimeStats]
  def getProductivityMetrics(userId: UserId, dateRange: DateRange): F[ProductivityMetrics]

  // Approval workflow
  def submitForApproval(userId: UserId, date: LocalDate): F[TimeApproval]
  def approveTime(approvalId: TimeApprovalId, approverId: UserId, comments: Option[String]): F[Option[TimeApproval]]
  def rejectTime(approvalId: TimeApprovalId, approverId: UserId, comments: String): F[Option[TimeApproval]]
  def listPendingApprovals(approverId: UserId): F[List[TimeApproval]]
}

case class DateRange(
  startDate: LocalDate,
  endDate: LocalDate
)

case class UserTimeStats(
  userId: UserId,
  dateRange: DateRange,
  totalWorkedHours: Double,
  averageDailyHours: Double,
  productivityRate: Double, // productive time / total time
  overtimeHours: Double,
  workDays: Int,
  breakTimeHours: Double
)

case class ProductivityMetrics(
  focusTimeHours: Double, // continuous work without breaks
  averageSessionLength: Double, // minutes
  breakFrequency: Double, // breaks per hour
  peakProductivityHours: List[Int], // hours of day (0-23)
  taskSwitchCount: Int,
  timePerTask: Double // average minutes per task
)
```

### 4. Time Tracking Service
**Fayl**: `endpoints/02-core/src/main/scala/tm/services/TimeTrackingService.scala`

```scala
trait TimeTrackingService[F[_]] {
  // Work session management
  def startWorkDay(userId: UserId, workMode: WorkMode, location: Option[String]): F[Either[TimeError, WorkSession]]
  def endWorkDay(userId: UserId): F[Either[TimeError, WorkSession]]
  def pauseWork(userId: UserId): F[Either[TimeError, Unit]]
  def resumeWork(userId: UserId): F[Either[TimeError, Unit]]
  def getCurrentWorkSession(userId: UserId): F[Option[WorkSession]]

  // Time entry management
  def startTimer(userId: UserId, taskId: Option[TaskId], description: String): F[Either[TimeError, TimeEntry]]
  def stopTimer(userId: UserId): F[Either[TimeError, TimeEntry]]
  def pauseTimer(userId: UserId): F[Either[TimeError, Unit]]
  def resumeTimer(userId: UserId): F[Either[TimeError, TimeEntry]]
  def switchTask(userId: UserId, newTaskId: TaskId, description: String): F[Either[TimeError, TimeEntry]]

  // Break management
  def startBreak(userId: UserId, reason: BreakReason, description: Option[String]): F[Either[TimeError, TimeEntry]]
  def endBreak(userId: UserId): F[Either[TimeError, TimeEntry]]
  def isOnBreak(userId: UserId): F[Boolean]

  // Manual time logging
  def logManualTime(
    userId: UserId,
    taskId: Option[TaskId],
    startTime: LocalDateTime,
    duration: Int, // minutes
    description: String
  ): F[Either[TimeError, TimeEntry]]

  def editTimeEntry(entryId: TimeEntryId, update: TimeEntryUpdate, userId: UserId): F[Either[TimeError, TimeEntry]]
  def deleteTimeEntry(entryId: TimeEntryId, userId: UserId): F[Either[TimeError, Unit]]

  // Dashboard data
  def getDashboardData(userId: UserId, date: LocalDate): F[TimeDashboard]
  def getWeekSummary(userId: UserId, weekStart: LocalDate): F[WeekTimeSummary]
  def getMonthSummary(userId: UserId, month: YearMonth): F[MonthTimeSummary]

  // Reports
  def generateTimeReport(userId: UserId, dateRange: DateRange, format: ReportFormat): F[Either[TimeError, TimeReport]]
  def exportTimeSheet(userId: UserId, month: YearMonth): F[Either[TimeError, Array[Byte]]] // Excel/PDF export

  // Analytics
  def getProductivityInsights(userId: UserId, dateRange: DateRange): F[ProductivityInsights]
  def getTimeDistribution(userId: UserId, dateRange: DateRange): F[TimeDistribution]
  def getWorkPatterns(userId: UserId, dateRange: DateRange): F[WorkPatterns]

  // Team management (for managers)
  def getTeamTimeOverview(managerId: UserId, teamUserIds: List[UserId], date: LocalDate): F[Either[TimeError, TeamTimeOverview]]
  def getTeamProductivity(managerId: UserId, teamUserIds: List[UserId], dateRange: DateRange): F[Either[TimeError, TeamProductivityReport]]

  // Approval workflow
  def submitTimeForApproval(userId: UserId, date: LocalDate): F[Either[TimeError, TimeApproval]]
  def approveTimeEntry(approvalId: TimeApprovalId, approverId: UserId, comments: Option[String]): F[Either[TimeError, Unit]]
  def rejectTimeEntry(approvalId: TimeApprovalId, approverId: UserId, comments: String): F[Either[TimeError, Unit]]

  // Settings and targets
  def getTimeTargets(userId: UserId): F[TimeTargets]
  def updateTimeTargets(userId: UserId, targets: TimeTargetsUpdate): F[Either[TimeError, TimeTargets]]
  def getWorkSchedule(userId: UserId): F[WorkSchedule]
  def updateWorkSchedule(userId: UserId, schedule: WorkScheduleUpdate): F[Either[TimeError, WorkSchedule]]
}

sealed trait TimeError
object TimeError {
  case object SessionNotFound extends TimeError
  case object TimerNotRunning extends TimeError
  case object TimerAlreadyRunning extends TimeError
  case object BreakNotActive extends TimeError
  case object InvalidTimeRange extends TimeError
  case object AccessDenied extends TimeError
  case class ValidationError(message: String) extends TimeError
}

case class TimeDashboard(
  currentSession: Option[WorkSession],
  currentTimer: Option[TimeEntry],
  todayStats: DailyTimeReport,
  weekProgress: WeekProgress,
  recentActivities: List[ActivityLog],
  productivityScore: Double
)

case class WeekProgress(
  weekStart: LocalDate,
  targetHours: Double,
  workedHours: Double,
  remainingHours: Double,
  progressPercentage: Double,
  dailyBreakdown: List[DailyProgress]
)

case class ProductivityInsights(
  focusTime: Double, // hours of uninterrupted work
  averageSessionLength: Double, // minutes
  mostProductiveHours: List[Int], // hours of day
  taskSwitchRate: Double, // switches per hour
  breakOptimization: BreakOptimizationSuggestion
)

case class TimeDistribution(
  byProject: Map[ProjectId, Double], // hours per project
  byTask: Map[TaskId, Double], // hours per task
  byCategory: Map[String, Double], // hours per task category
  productiveVsBreak: (Double, Double) // (productive hours, break hours)
)
```

### 5. API Routes
**Fayl**: `endpoints/03-api/src/main/scala/tm/endpoint/routes/TimeTrackingRoutes.scala`

```scala
object TimeTrackingRoutes {
  def routes[F[_]: Async](
    timeService: TimeTrackingService[F],
    authMiddleware: AuthMiddleware[F]
  ): HttpRoutes[F] = {

    val protectedRoutes: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of[AuthedUser, F] {
      // Work session management
      case POST -> Root / "session" / "start" as user =>
        // Start work day

      case POST -> Root / "session" / "end" as user =>
        // End work day

      case GET -> Root / "session" / "current" as user =>
        // Get current work session

      case POST -> Root / "session" / "pause" as user =>
        // Pause work session

      case POST -> Root / "session" / "resume" as user =>
        // Resume work session

      // Timer management
      case POST -> Root / "timer" / "start" as user =>
        // Start task timer

      case POST -> Root / "timer" / "stop" as user =>
        // Stop current timer

      case GET -> Root / "timer" / "current" as user =>
        // Get current timer

      case POST -> Root / "timer" / "switch" as user =>
        // Switch to different task

      // Break management
      case POST -> Root / "break" / "start" as user =>
        // Start break

      case POST -> Root / "break" / "end" as user =>
        // End break

      case GET -> Root / "break" / "current" as user =>
        // Get current break status

      // Time entries
      case GET -> Root / "entries" :? DateRangeQueryParam(range) as user =>
        // List time entries

      case POST -> Root / "entries" / "manual" as user =>
        // Log manual time entry

      case PUT -> Root / "entries" / UUIDVar(entryId) as user =>
        // Update time entry

      case DELETE -> Root / "entries" / UUIDVar(entryId) as user =>
        // Delete time entry

      // Dashboard and reports
      case GET -> Root / "dashboard" :? DateQueryParam(date) as user =>
        // Get dashboard data

      case GET -> Root / "reports" / "daily" / DateVar(date) as user =>
        // Get daily report

      case GET -> Root / "reports" / "weekly" / DateVar(weekStart) as user =>
        // Get weekly report

      case GET -> Root / "reports" / "monthly" / IntVar(year) / IntVar(month) as user =>
        // Get monthly report

      case GET -> Root / "analytics" / "productivity" :? DateRangeQueryParam(range) as user =>
        // Get productivity analytics

      case GET -> Root / "analytics" / "distribution" :? DateRangeQueryParam(range) as user =>
        // Get time distribution

      // Export
      case GET -> Root / "export" / "timesheet" / IntVar(year) / IntVar(month) as user =>
        // Export monthly timesheet

      // Team management (for managers)
      case GET -> Root / "team" / "overview" :? DateQueryParam(date) as user =>
        // Team time overview

      case GET -> Root / "team" / "productivity" :? DateRangeQueryParam(range) as user =>
        // Team productivity report

      // Approval workflow
      case POST -> Root / "approvals" / DateVar(date) / "submit" as user =>
        // Submit time for approval

      case GET -> Root / "approvals" / "pending" as user =>
        // List pending approvals

      case POST -> Root / "approvals" / UUIDVar(approvalId) / "approve" as user =>
        // Approve time entry

      case POST -> Root / "approvals" / UUIDVar(approvalId) / "reject" as user =>
        // Reject time entry

      // Settings
      case GET -> Root / "targets" as user =>
        // Get time targets

      case PUT -> Root / "targets" as user =>
        // Update time targets

      case GET -> Root / "schedule" as user =>
        // Get work schedule

      case PUT -> Root / "schedule" as user =>
        // Update work schedule
    }

    authMiddleware(protectedRoutes)
  }
}
```

### 6. API Documentation

#### POST /api/time/session/start
```json
{
  "workMode": "Office",
  "location": "Main Office, Floor 3",
  "description": "Starting daily work"
}
```

#### POST /api/time/timer/start
```json
{
  "taskId": "uuid",
  "description": "Working on user authentication feature"
}
```

#### GET /api/time/dashboard?date=2024-01-15
```json
{
  "currentSession": {
    "id": "uuid",
    "startTime": "2024-01-15T09:00:00Z",
    "workMode": "Office",
    "totalMinutes": 240,
    "productiveMinutes": 200,
    "breakMinutes": 40,
    "isRunning": true
  },
  "currentTimer": {
    "id": "uuid",
    "taskId": "uuid",
    "startTime": "2024-01-15T13:00:00Z",
    "description": "Implementing time tracking API",
    "isRunning": true
  },
  "todayStats": {
    "date": "2024-01-15",
    "totalWorkedMinutes": 240,
    "productiveMinutes": 200,
    "breakMinutes": 40,
    "tasksWorked": 3,
    "overtimeMinutes": 0
  },
  "weekProgress": {
    "weekStart": "2024-01-15",
    "targetHours": 40,
    "workedHours": 24.5,
    "remainingHours": 15.5,
    "progressPercentage": 61.25
  },
  "productivityScore": 85.5
}
```

## Real-time Updates

WebSocket support for real-time timer updates:

```scala
// WebSocket endpoint for real-time timer updates
case GET -> Root / "timer" / "websocket" as user =>
  // WebSocket connection for timer updates
```

## Testing Strategy

1. **Unit Tests**: Time calculations va business logic
2. **Integration Tests**: Timer state transitions
3. **Concurrency Tests**: Multiple timer operations
4. **Performance Tests**: Large time entry datasets

## Estimated Time: 2-3 hafta