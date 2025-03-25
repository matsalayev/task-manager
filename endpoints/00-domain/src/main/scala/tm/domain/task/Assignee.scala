package tm.domain.task

import tm.domain.PersonId
import tm.domain.TaskId

case class Assignee(
    taskId: TaskId,
    employeeId: List[PersonId],
  )
