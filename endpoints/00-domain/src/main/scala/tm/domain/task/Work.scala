package tm.domain.task

import java.time.ZonedDateTime

import eu.timepit.refined.types.all.NonNegBigInt

import tm.domain.EmployeeId
import tm.domain.TaskId
import tm.domain.WorkId

case class Work(
    id: WorkId,
    createdAt: ZonedDateTime,
    employeeId: EmployeeId,
    taskId: TaskId,
    duringMinutes: NonNegBigInt,
    finishedAt: Option[ZonedDateTime],
  )
