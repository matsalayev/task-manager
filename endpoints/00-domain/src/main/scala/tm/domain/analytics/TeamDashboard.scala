package tm.domain.analytics

import java.time.LocalDate
import java.time.LocalDateTime

import enumeratum._
import io.circe.Codec
import io.circe.generic.semiauto._

import tm.domain.PersonId
import tm.domain.ProjectId
import tm.syntax.circe._

case class TeamDashboard(
    managerId: PersonId,
    teamStats: TeamStats,
    memberOverviews: List[TeamMemberOverview],
    teamGoals: TeamGoals,
    alerts: List[TeamAlert],
    projectProgress: List[ProjectProgress],
  )

case class TeamStats(
    totalMembers: Int,
    activeMembers: Int,
    todayHours: Double,
    weekHours: Double,
    productivity: Double,
    onTimeDelivery: Double,
    memberSatisfaction: Option[Double],
  )

case class AnalyticsUser(
    id: PersonId,
    name: String,
    email: String,
    department: Option[String],
    position: Option[String],
  )

case class TeamMemberOverview(
    user: AnalyticsUser,
    todayStats: MemberDayStats,
    weekStats: MemberWeekStats,
    productivity: MemberProductivity,
    currentStatus: MemberStatus,
    workload: WorkloadLevel,
  )

case class MemberDayStats(
    hoursWorked: Double,
    tasksCompleted: Int,
    efficiency: Double,
    isWorking: Boolean,
    currentTask: Option[String],
  )

case class MemberWeekStats(
    totalHours: Double,
    averageDaily: Double,
    tasksCompleted: Int,
    productivity: Double,
  )

case class MemberProductivity(
    score: Double,
    trend: TrendDirection,
    peakHours: List[Int],
    focusTime: Double,
  )

sealed trait MemberStatus extends EnumEntry
object MemberStatus extends Enum[MemberStatus] with CirceEnum[MemberStatus] {
  case object Working extends MemberStatus
  case object OnBreak extends MemberStatus
  case object Offline extends MemberStatus
  case object InMeeting extends MemberStatus

  val values = findValues
}

sealed trait WorkloadLevel extends EnumEntry
object WorkloadLevel extends Enum[WorkloadLevel] with CirceEnum[WorkloadLevel] {
  case object Low extends WorkloadLevel
  case object Normal extends WorkloadLevel
  case object High extends WorkloadLevel
  case object Overloaded extends WorkloadLevel

  val values = findValues
}

case class TeamGoals(
    weeklyHoursTarget: Double,
    monthlyTasksTarget: Int,
    productivityTarget: Double,
    currentProgress: TeamGoalProgress,
  )

case class TeamGoalProgress(
    hoursProgress: Double,
    tasksProgress: Double,
    productivityProgress: Double,
  )

case class TeamAlert(
    id: String,
    alertType: TeamAlertType,
    severity: AlertSeverity,
    title: String,
    description: String,
    affectedMembers: List[PersonId],
    createdAt: LocalDateTime,
    isResolved: Boolean,
  )

sealed trait TeamAlertType extends EnumEntry
object TeamAlertType extends Enum[TeamAlertType] with CirceEnum[TeamAlertType] {
  case object DeadlineMissed extends TeamAlertType
  case object OverworkedMember extends TeamAlertType
  case object LowProductivity extends TeamAlertType
  case object InactiveMember extends TeamAlertType
  case object ProjectDelay extends TeamAlertType

  val values = findValues
}

sealed trait AlertSeverity extends EnumEntry
object AlertSeverity extends Enum[AlertSeverity] with CirceEnum[AlertSeverity] {
  case object Info extends AlertSeverity
  case object Warning extends AlertSeverity
  case object Critical extends AlertSeverity

  val values = findValues
}

case class ProjectProgress(
    projectId: ProjectId,
    projectName: String,
    totalTasks: Int,
    completedTasks: Int,
    inProgressTasks: Int,
    completionPercentage: Double,
    estimatedCompletion: Option[LocalDate],
    teamMembers: List[PersonId],
    isOnTrack: Boolean,
  )

// Executive Dashboard Models
case class ExecutiveDashboard(
    companyStats: CompanyStats,
    departmentStats: List[DepartmentStats],
    keyMetrics: List[KPIMetric],
    trends: List[TrendAnalysis],
    alerts: List[ExecutiveAlert],
    upcomingDeadlines: List[DeadlineAlert],
  )

case class CompanyStats(
    totalEmployees: Int,
    activeEmployees: Int,
    totalProjects: Int,
    activeProjects: Int,
    overallProductivity: Double,
    averageWorkHours: Double,
    completedTasks: Int,
    revenue: Option[Double],
  )

case class DepartmentStats(
    departmentName: String,
    employeeCount: Int,
    productivity: Double,
    hoursWorked: Double,
    projectsCount: Int,
    efficiency: Double,
  )

case class KPIMetric(
    name: String,
    value: Double,
    target: Double,
    unit: String,
    trend: TrendDirection,
    changePercentage: Double,
  )

case class TrendAnalysis(
    metric: String,
    period: String,
    direction: TrendDirection,
    changePercentage: Double,
    insights: List[String],
  )

case class ExecutiveAlert(
    id: String,
    alertType: ExecutiveAlertType,
    severity: AlertSeverity,
    title: String,
    description: String,
    affectedDepartments: List[String],
    createdAt: LocalDateTime,
    requiresAction: Boolean,
  )

sealed trait ExecutiveAlertType extends EnumEntry
object ExecutiveAlertType extends Enum[ExecutiveAlertType] with CirceEnum[ExecutiveAlertType] {
  case object ProductivityDrop extends ExecutiveAlertType
  case object BudgetOverrun extends ExecutiveAlertType
  case object DeadlineMissed extends ExecutiveAlertType
  case object HighTurnover extends ExecutiveAlertType
  case object SystemIssue extends ExecutiveAlertType

  val values = findValues
}

case class DeadlineAlert(
    projectId: ProjectId,
    projectName: String,
    deadline: LocalDate,
    daysRemaining: Int,
    completionPercentage: Double,
    riskLevel: RiskLevel,
  )

sealed trait RiskLevel extends EnumEntry
object RiskLevel extends Enum[RiskLevel] with CirceEnum[RiskLevel] {
  case object Low extends RiskLevel
  case object Medium extends RiskLevel
  case object High extends RiskLevel
  case object Critical extends RiskLevel

  val values = findValues
}

// Codecs
object TeamDashboard {
  implicit val codec: Codec[TeamDashboard] = deriveCodec
}

object TeamStats {
  implicit val codec: Codec[TeamStats] = deriveCodec
}

object AnalyticsUser {
  implicit val codec: Codec[AnalyticsUser] = deriveCodec
}

object TeamMemberOverview {
  implicit val codec: Codec[TeamMemberOverview] = deriveCodec
}

object MemberDayStats {
  implicit val codec: Codec[MemberDayStats] = deriveCodec
}

object MemberWeekStats {
  implicit val codec: Codec[MemberWeekStats] = deriveCodec
}

object MemberProductivity {
  implicit val codec: Codec[MemberProductivity] = deriveCodec
}

object TeamGoals {
  implicit val codec: Codec[TeamGoals] = deriveCodec
}

object TeamGoalProgress {
  implicit val codec: Codec[TeamGoalProgress] = deriveCodec
}

object TeamAlert {
  implicit val codec: Codec[TeamAlert] = deriveCodec
}

object ProjectProgress {
  implicit val codec: Codec[ProjectProgress] = deriveCodec
}

object ExecutiveDashboard {
  implicit val codec: Codec[ExecutiveDashboard] = deriveCodec
}

object CompanyStats {
  implicit val codec: Codec[CompanyStats] = deriveCodec
}

object DepartmentStats {
  implicit val codec: Codec[DepartmentStats] = deriveCodec
}

object KPIMetric {
  implicit val codec: Codec[KPIMetric] = deriveCodec
}

object TrendAnalysis {
  implicit val codec: Codec[TrendAnalysis] = deriveCodec
}

object ExecutiveAlert {
  implicit val codec: Codec[ExecutiveAlert] = deriveCodec
}

object DeadlineAlert {
  implicit val codec: Codec[DeadlineAlert] = deriveCodec
}
