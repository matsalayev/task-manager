package tm.domain.task

import tm.domain.EmployeeId
import tm.domain.TaskId

case class Assignee(
    taskId: TaskId,
    employeeId: List[EmployeeId],
  )
