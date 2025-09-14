# Leave Management Backend Implementation

## UI Requirements Analysis

### Qo'shilish Kerak:
- Leave request creation and approval
- Calendar integration
- Leave balance tracking
- Holiday management
- Absence reporting

## Implementation Tasks

### 1. Leave Domain Models
**Priority: 🟢 Low**
**Fayl**: `endpoints/00-domain/src/main/scala/tm/domain/leave/`

```scala
case class LeaveRequest(
  id: LeaveRequestId,
  employeeId: UserId,
  leaveType: LeaveType,
  startDate: LocalDate,
  endDate: LocalDate,
  totalDays: Int,
  reason: String,
  status: LeaveStatus,
  approverId: Option[UserId],
  approvedAt: Option[Instant],
  comments: Option[String],
  createdAt: Instant,
  updatedAt: Instant
)

sealed trait LeaveType
object LeaveType {
  case object Annual extends LeaveType
  case object Sick extends LeaveType
  case object Personal extends LeaveType
  case object Maternity extends LeaveType
  case object Paternity extends LeaveType
  case object Emergency extends LeaveType
}

sealed trait LeaveStatus
object LeaveStatus {
  case object Pending extends LeaveStatus
  case object Approved extends LeaveStatus
  case object Rejected extends LeaveStatus
  case object Cancelled extends LeaveStatus
}

case class LeaveBalance(
  userId: UserId,
  year: Int,
  leaveType: LeaveType,
  totalDays: Int,
  usedDays: Int,
  pendingDays: Int,
  remainingDays: Int,
  updatedAt: Instant
)

case class Holiday(
  id: HolidayId,
  name: String,
  date: LocalDate,
  isRecurring: Boolean,
  countryCode: Option[String],
  companyId: Option[CompanyId],
  createdAt: Instant
)
```

## Estimated Time: 1-2 hafta