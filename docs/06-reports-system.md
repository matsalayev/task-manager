# Reports System Backend Implementation

## UI Requirements Analysis

### Qo'shilish Kerak:
- Executive dashboard reports
- Team performance comparison
- Time vs Budget analysis
- Custom report builder
- Automated report generation
- Export capabilities (PDF, Excel, CSV)

## Implementation Tasks

### 1. Reports Domain Models
**Priority: 🟡 Medium**
**Fayl**: `endpoints/00-domain/src/main/scala/tm/domain/reports/`

```scala
case class Report(
  id: ReportId,
  name: String,
  description: Option[String],
  reportType: ReportType,
  createdBy: UserId,
  companyId: CompanyId,
  config: ReportConfig,
  schedule: Option[ReportSchedule],
  isPublic: Boolean,
  createdAt: Instant,
  updatedAt: Instant
)

sealed trait ReportType
object ReportType {
  case object TimeTracking extends ReportType
  case object Productivity extends ReportType
  case object ProjectProgress extends ReportType
  case object TeamPerformance extends ReportType
  case object Financial extends ReportType
  case object Custom extends ReportType
}

case class ReportConfig(
  dateRange: DateRangeConfig,
  groupBy: List[GroupByField],
  metrics: List[MetricConfig],
  filters: Map[String, FilterValue],
  chartTypes: List[ChartType],
  includeComparisons: Boolean,
  includeTrends: Boolean
)

case class ReportSchedule(
  frequency: ScheduleFrequency,
  dayOfWeek: Option[Int], // 1-7
  dayOfMonth: Option[Int], // 1-31
  time: LocalTime,
  recipients: List[UserId],
  format: ReportFormat,
  isActive: Boolean
)

sealed trait ScheduleFrequency
object ScheduleFrequency {
  case object Daily extends ScheduleFrequency
  case object Weekly extends ScheduleFrequency
  case object Monthly extends ScheduleFrequency
  case object Quarterly extends ScheduleFrequency
}

case class GeneratedReport(
  id: GeneratedReportId,
  reportId: ReportId,
  generatedBy: UserId,
  dateRange: DateRange,
  format: ReportFormat,
  filePath: Option[String],
  fileSize: Option[Long],
  status: ReportStatus,
  data: ReportData,
  generatedAt: Instant,
  expiresAt: Option[Instant]
)

sealed trait ReportFormat
object ReportFormat {
  case object PDF extends ReportFormat
  case object Excel extends ReportFormat
  case object CSV extends ReportFormat
  case object JSON extends ReportFormat
}

case class ReportData(
  summary: ReportSummary,
  sections: List[ReportSection],
  charts: List[ChartData],
  tables: List[TableData]
)

case class TeamPerformanceReport(
  teamId: TeamId,
  dateRange: DateRange,
  summary: TeamSummary,
  memberStats: List[MemberPerformanceStats],
  projectContributions: List[ProjectContribution],
  goals: TeamGoalsReport,
  recommendations: List[TeamRecommendation]
)

case class ProjectProgressReport(
  projectId: ProjectId,
  dateRange: DateRange,
  progress: ProjectProgressStats,
  milestones: List[MilestoneProgress],
  teamPerformance: ProjectTeamStats,
  budget: Option[ProjectBudgetReport],
  risks: List[ProjectRisk],
  forecast: ProjectForecast
)

case class TimeTrackingReport(
  userId: Option[UserId],
  teamId: Option[TeamId],
  dateRange: DateRange,
  totalHours: Double,
  billableHours: Double,
  utilization: Double,
  breakdown: TimeBreakdown,
  trends: TimeTrends,
  anomalies: List[TimeAnomaly]
)
```

### 2. Reports Repository
**Fayl**: `endpoints/01-repos/src/main/scala/tm/repos/ReportsRepo.scala`

```scala
trait ReportsRepo[F[_]] {
  // Report templates
  def createReport(report: ReportCreate): F[Report]
  def updateReport(reportId: ReportId, update: ReportUpdate): F[Option[Report]]
  def deleteReport(reportId: ReportId): F[Boolean]
  def findReport(reportId: ReportId): F[Option[Report]]
  def listReports(companyId: CompanyId, userId: UserId): F[List[Report]]

  // Generated reports
  def generateReport(reportId: ReportId, userId: UserId, dateRange: Option[DateRange]): F[GeneratedReport]
  def getGeneratedReport(generatedReportId: GeneratedReportId): F[Option[GeneratedReport]]
  def listGeneratedReports(reportId: ReportId, limit: Int): F[List[GeneratedReport]]
  def deleteExpiredReports(): F[Int]

  // Scheduled reports
  def scheduleReport(reportId: ReportId, schedule: ReportSchedule): F[Report]
  def getScheduledReports(): F[List[Report]]
  def updateSchedule(reportId: ReportId, schedule: ReportSchedule): F[Option[Report]]

  // Data aggregation
  def aggregateTimeData(query: TimeDataQuery): F[List[TimeDataPoint]]
  def aggregateProjectData(query: ProjectDataQuery): F[List[ProjectDataPoint]]
  def aggregateTeamData(query: TeamDataQuery): F[List[TeamDataPoint]]
  def aggregateUserData(query: UserDataQuery): F[List[UserDataPoint]]

  // Analytics queries
  def getProductivityMetrics(query: ProductivityQuery): F[ProductivityMetrics]
  def getTeamComparison(teamIds: List[TeamId], dateRange: DateRange): F[TeamComparisonData]
  def getUserComparison(userIds: List[UserId], dateRange: DateRange): F[UserComparisonData]
  def getProjectComparison(projectIds: List[ProjectId], dateRange: DateRange): F[ProjectComparisonData]

  // KPI calculations
  def calculateKPIs(companyId: CompanyId, dateRange: DateRange): F[List[KPIResult]]
  def getTrendAnalysis(metric: String, companyId: CompanyId, dateRange: DateRange): F[TrendAnalysis]

  // Export utilities
  def exportToPDF(reportData: ReportData, template: String): F[Array[Byte]]
  def exportToExcel(reportData: ReportData): F[Array[Byte]]
  def exportToCSV(tableData: List[TableData]): F[String]
}

case class TimeDataQuery(
  userIds: Option[List[UserId]],
  projectIds: Option[List[ProjectId]],
  dateRange: DateRange,
  granularity: TimeGranularity,
  groupBy: List[String],
  filters: Map[String, String]
)

case class KPIResult(
  name: String,
  value: Double,
  target: Option[Double],
  trend: TrendDirection,
  unit: String,
  category: String
)
```

### 3. Reports Service
**Fayl**: `endpoints/02-core/src/main/scala/tm/services/ReportsService.scala`

```scala
trait ReportsService[F[_]] {
  // Report management
  def createReport(create: ReportCreate, userId: UserId): F[Either[ReportError, Report]]
  def updateReport(reportId: ReportId, update: ReportUpdate, userId: UserId): F[Either[ReportError, Report]]
  def deleteReport(reportId: ReportId, userId: UserId): F[Either[ReportError, Unit]]
  def getReport(reportId: ReportId, userId: UserId): F[Either[ReportError, Report]]
  def listUserReports(userId: UserId): F[List[Report]]

  // Report generation
  def generateReport(reportId: ReportId, userId: UserId, options: GenerationOptions): F[Either[ReportError, GeneratedReport]]
  def regenerateReport(generatedReportId: GeneratedReportId, userId: UserId): F[Either[ReportError, GeneratedReport]]
  def getGeneratedReport(generatedReportId: GeneratedReportId, userId: UserId): F[Either[ReportError, GeneratedReport]]

  // Predefined reports
  def generateTimeTrackingReport(query: TimeTrackingQuery, userId: UserId): F[Either[ReportError, TimeTrackingReport]]
  def generateTeamPerformanceReport(teamId: TeamId, dateRange: DateRange, userId: UserId): F[Either[ReportError, TeamPerformanceReport]]
  def generateProjectProgressReport(projectId: ProjectId, userId: UserId): F[Either[ReportError, ProjectProgressReport]]
  def generateProductivityReport(query: ProductivityQuery, userId: UserId): F[Either[ReportError, ProductivityReport]]

  // Executive reports
  def generateExecutiveSummary(companyId: CompanyId, dateRange: DateRange, userId: UserId): F[Either[ReportError, ExecutiveSummary]]
  def generateKPIDashboard(companyId: CompanyId, userId: UserId): F[Either[ReportError, KPIDashboard]]
  def generateDepartmentComparison(companyId: CompanyId, dateRange: DateRange, userId: UserId): F[Either[ReportError, DepartmentComparison]]

  // Scheduled reports
  def scheduleReport(reportId: ReportId, schedule: ReportSchedule, userId: UserId): F[Either[ReportError, Report]]
  def updateSchedule(reportId: ReportId, schedule: ReportSchedule, userId: UserId): F[Either[ReportError, Report]]
  def pauseScheduledReport(reportId: ReportId, userId: UserId): F[Either[ReportError, Unit]]
  def resumeScheduledReport(reportId: ReportId, userId: UserId): F[Either[ReportError, Unit]]

  // Background processing
  def processScheduledReports(): F[List[GeneratedReport]]
  def sendScheduledReport(generatedReportId: GeneratedReportId): F[Either[ReportError, Unit]]
  def cleanupExpiredReports(): F[Int]

  // Export functions
  def exportReportAsPDF(generatedReportId: GeneratedReportId, userId: UserId): F[Either[ReportError, Array[Byte]]]
  def exportReportAsExcel(generatedReportId: GeneratedReportId, userId: UserId): F[Either[ReportError, Array[Byte]]]
  def exportReportAsCSV(generatedReportId: GeneratedReportId, userId: UserId): F[Either[ReportError, String]]

  // Sharing and collaboration
  def shareReport(reportId: ReportId, userIds: List[UserId], permissions: SharePermissions, userId: UserId): F[Either[ReportError, Unit]]
  def getSharedReports(userId: UserId): F[List[Report]]
  def addReportComment(reportId: ReportId, comment: String, userId: UserId): F[Either[ReportError, ReportComment]]

  // Analytics
  def getReportAnalytics(reportId: ReportId, userId: UserId): F[Either[ReportError, ReportAnalytics]]
  def getPopularReports(companyId: CompanyId): F[List[ReportPopularity]]
}

sealed trait ReportError
object ReportError {
  case object ReportNotFound extends ReportError
  case object AccessDenied extends ReportError
  case object InsufficientData extends ReportError
  case object GenerationFailed extends ReportError
  case object ExportFailed extends ReportError
  case class ValidationError(message: String) extends ReportError
}

case class ExecutiveSummary(
  companyId: CompanyId,
  dateRange: DateRange,
  keyMetrics: ExecutiveMetrics,
  departmentSummaries: List[DepartmentSummary],
  projectHighlights: List[ProjectHighlight],
  alerts: List[ExecutiveAlert],
  recommendations: List[ExecutiveRecommendation]
)

case class ExecutiveMetrics(
  totalEmployees: Int,
  activeProjects: Int,
  completedTasks: Int,
  averageProductivity: Double,
  totalRevenue: Option[BigDecimal],
  budgetUtilization: Double,
  clientSatisfaction: Option[Double]
)
```

### 4. Report Templates Engine
**Fayl**: `endpoints/02-core/src/main/scala/tm/services/ReportTemplateEngine.scala`

```scala
trait ReportTemplateEngine[F[_]] {
  def renderPDFReport(template: String, data: ReportData, config: PDFConfig): F[Array[Byte]]
  def renderExcelReport(data: ReportData, config: ExcelConfig): F[Array[Byte]]
  def renderHTMLReport(template: String, data: ReportData): F[String]
  def generateCharts(chartConfigs: List[ChartConfig], data: List[ChartData]): F[List[GeneratedChart]]
}

case class PDFConfig(
  pageSize: PageSize,
  orientation: Orientation,
  margins: Margins,
  includeCharts: Boolean,
  includeTables: Boolean,
  headerTemplate: Option[String],
  footerTemplate: Option[String]
)

case class ExcelConfig(
  includeCharts: Boolean,
  includePivotTables: Boolean,
  worksheetNames: Map[String, String],
  formatting: ExcelFormatting
)
```

### 5. Database Schema
**Migration**: `endpoints/01-repos/src/main/resources/db/migration/V008__reports.sql`

```sql
-- Report templates
CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    report_type VARCHAR(50) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    company_id UUID NOT NULL REFERENCES companies(id),
    config JSONB NOT NULL,
    schedule_config JSONB,
    is_public BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Generated reports
CREATE TABLE generated_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    generated_by UUID NOT NULL REFERENCES users(id),
    date_range_start DATE NOT NULL,
    date_range_end DATE NOT NULL,
    format VARCHAR(20) NOT NULL,
    file_path TEXT,
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'Generating',
    data JSONB,
    generated_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ,

    INDEX idx_generated_reports_report(report_id),
    INDEX idx_generated_reports_user(generated_by),
    INDEX idx_generated_reports_status(status)
);

-- Report sharing
CREATE TABLE report_shares (
    report_id UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    shared_with UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shared_by UUID NOT NULL REFERENCES users(id),
    permissions VARCHAR(20) DEFAULT 'view', -- view, edit, admin
    shared_at TIMESTAMPTZ DEFAULT NOW(),

    PRIMARY KEY (report_id, shared_with)
);

-- Report comments
CREATE TABLE report_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),

    INDEX idx_report_comments_report(report_id)
);

-- KPI definitions
CREATE TABLE kpi_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    calculation_method TEXT NOT NULL,
    target_value DECIMAL(15,4),
    unit VARCHAR(50),
    category VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE(company_id, name)
);

-- KPI values (historical tracking)
CREATE TABLE kpi_values (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kpi_id UUID NOT NULL REFERENCES kpi_definitions(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    value DECIMAL(15,4) NOT NULL,
    target DECIMAL(15,4),
    calculated_at TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE(kpi_id, period_start, period_end),
    INDEX idx_kpi_values_period(kpi_id, period_start, period_end)
);

-- Report subscriptions (for automated delivery)
CREATE TABLE report_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    delivery_method VARCHAR(20) DEFAULT 'email', -- email, slack, webhook
    delivery_address TEXT NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE(report_id, user_id, delivery_method)
);
```

### 6. API Routes
**Fayl**: `endpoints/03-api/src/main/scala/tm/endpoint/routes/ReportsRoutes.scala`

```scala
object ReportsRoutes {
  def routes[F[_]: Async](
    reportsService: ReportsService[F],
    authMiddleware: AuthMiddleware[F]
  ): HttpRoutes[F] = {

    val protectedRoutes: AuthedRoutes[AuthedUser, F] = AuthedRoutes.of[AuthedUser, F] {
      // Report templates
      case GET -> Root as user =>
        // List user reports

      case POST -> Root as user =>
        // Create new report template

      case GET -> Root / UUIDVar(reportId) as user =>
        // Get report template

      case PUT -> Root / UUIDVar(reportId) as user =>
        // Update report template

      case DELETE -> Root / UUIDVar(reportId) as user =>
        // Delete report template

      // Report generation
      case POST -> Root / UUIDVar(reportId) / "generate" as user =>
        // Generate report

      case GET -> Root / "generated" / UUIDVar(generatedReportId) as user =>
        // Get generated report

      case GET -> Root / UUIDVar(reportId) / "generated" as user =>
        // List generated reports for template

      // Export functionality
      case GET -> Root / "generated" / UUIDVar(generatedReportId) / "download" / "pdf" as user =>
        // Download as PDF

      case GET -> Root / "generated" / UUIDVar(generatedReportId) / "download" / "excel" as user =>
        // Download as Excel

      case GET -> Root / "generated" / UUIDVar(generatedReportId) / "download" / "csv" as user =>
        // Download as CSV

      // Predefined reports
      case POST -> Root / "predefined" / "time-tracking" as user =>
        // Generate time tracking report

      case POST -> Root / "predefined" / "team-performance" as user =>
        // Generate team performance report

      case POST -> Root / "predefined" / "project-progress" as user =>
        // Generate project progress report

      case POST -> Root / "predefined" / "executive-summary" as user =>
        // Generate executive summary

      // Scheduled reports
      case POST -> Root / UUIDVar(reportId) / "schedule" as user =>
        // Schedule report

      case PUT -> Root / UUIDVar(reportId) / "schedule" as user =>
        // Update schedule

      case DELETE -> Root / UUIDVar(reportId) / "schedule" as user =>
        // Remove schedule

      case GET -> Root / "scheduled" as user =>
        // List scheduled reports

      // Sharing
      case POST -> Root / UUIDVar(reportId) / "share" as user =>
        // Share report

      case GET -> Root / "shared" as user =>
        // List shared reports

      case POST -> Root / UUIDVar(reportId) / "comments" as user =>
        // Add comment

      // Analytics
      case GET -> Root / UUIDVar(reportId) / "analytics" as user =>
        // Report analytics

      case GET -> Root / "popular" as user =>
        // Popular reports
    }

    authMiddleware(protectedRoutes)
  }
}
```

### 7. Background Jobs
**Fayl**: `endpoints/03-jobs/src/main/scala/tm/jobs/ReportJobs.scala`

```scala
object ReportJobs {
  // Scheduled report generation
  def scheduledReportsJob[F[_]: Async](
    reportsService: ReportsService[F]
  ): F[Unit] = {
    // Runs every hour to process scheduled reports
    for {
      reports <- reportsService.processScheduledReports()
      _ <- reports.traverse(report => reportsService.sendScheduledReport(report.id))
    } yield ()
  }

  // Cleanup expired reports
  def cleanupJob[F[_]: Async](
    reportsService: ReportsService[F]
  ): F[Unit] = {
    // Runs daily to cleanup expired reports
    reportsService.cleanupExpiredReports().void
  }

  // KPI calculation job
  def kpiCalculationJob[F[_]: Async](
    reportsService: ReportsService[F]
  ): F[Unit] = {
    // Runs daily to calculate and store KPI values
    // Implementation for automated KPI calculation
  }
}
```

## API Documentation Examples

#### POST /api/reports/predefined/time-tracking
```json
{
  "dateRange": {
    "startDate": "2024-01-01",
    "endDate": "2024-01-31"
  },
  "userIds": ["uuid1", "uuid2"],
  "groupBy": ["user", "project"],
  "includeBreakdown": true,
  "format": "PDF"
}
```

#### POST /api/reports/{id}/schedule
```json
{
  "frequency": "Weekly",
  "dayOfWeek": 1,
  "time": "09:00:00",
  "recipients": ["uuid1", "uuid2"],
  "format": "PDF",
  "isActive": true
}
```

## Testing Strategy

1. **Unit Tests**: Report generation logic va calculations
2. **Integration Tests**: End-to-end report generation
3. **Performance Tests**: Large dataset report generation
4. **Format Tests**: PDF, Excel, CSV export quality

## Estimated Time: 2-3 hafta