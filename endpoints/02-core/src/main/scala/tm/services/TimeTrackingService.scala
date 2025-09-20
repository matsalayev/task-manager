package tm.services

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import cats.MonadThrow
import cats.effect.Sync
import cats.implicits._

import tm.domain.PersonId
import tm.domain.TaskId
import tm.domain.WorkId
import tm.domain.time._
import tm.effects.Calendar
import tm.effects.GenUUID
import tm.exception.AError
import tm.repositories.TimeTrackingRepository
import tm.utils.ID

trait TimeTrackingService[F[_]] {
  def startWorkSession(userId: PersonId, request: StartWorkSessionRequest): F[EnhancedWorkSession]
  def endWorkSession(userId: PersonId): F[Option[EnhancedWorkSession]]
  def getCurrentSession(userId: PersonId): F[Option[EnhancedWorkSession]]
  def getWorkSessions(
      userId: PersonId,
      limit: Int = 20,
      page: Int = 1,
    ): F[List[EnhancedWorkSession]]

  def startTimer(userId: PersonId, request: StartTimerRequest): F[TimeEntry]
  def endTimer(userId: PersonId, entryId: TimeEntryId): F[Option[TimeEntry]]
  def startBreak(userId: PersonId, request: StartBreakRequest): F[TimeEntry]
  def endBreak(userId: PersonId): F[Option[TimeEntry]]
  def addManualEntry(userId: PersonId, request: ManualTimeEntryRequest): F[TimeEntry]

  def getTimeEntries(userId: PersonId, taskId: Option[TaskId] = None): F[List[TimeEntry]]
  def getRunningEntries(userId: PersonId): F[List[TimeEntry]]

  def getTimeDashboard(userId: PersonId): F[TimeDashboard]
  def getDailyReport(userId: PersonId, date: java.time.LocalDate): F[DailyTimeReport]
  def getWeeklyReport(userId: PersonId, weekStart: java.time.LocalDate): F[WeeklyTimeReport]
  def getProductivityMetrics(userId: PersonId): F[ProductivityMetrics]
}

object TimeTrackingService {
  def make[F[_]: Sync: MonadThrow: Calendar: GenUUID](
      timeRepo: TimeTrackingRepository[F]
    ): TimeTrackingService[F] =
    new TimeTrackingService[F] {
      override def startWorkSession(
          userId: PersonId,
          request: StartWorkSessionRequest,
        ): F[EnhancedWorkSession] =
        for {
          // End any existing active sessions
          _ <- endWorkSession(userId)

          sessionId <- ID.make[F, WorkId]
          now <- Calendar[F].currentZonedDateTime
          nowLocal <- Sync[F].delay(now.toLocalDateTime)

          session = EnhancedWorkSession(
            id = sessionId,
            userId = userId,
            startTime = nowLocal,
            endTime = None,
            workMode = request.workMode,
            isRunning = true,
            totalMinutes = 0,
            breakMinutes = 0,
            productiveMinutes = 0,
            description = request.description,
            location = request.location,
            createdAt = now,
          )

          created <- timeRepo.startWorkSession(session)

          // Log activity
          activityId <- ID.make[F, ActivityLogId]
          activity = ActivityLog(
            id = activityId,
            userId = userId,
            activityType = ActivityType.SessionStart,
            timestamp = nowLocal,
            metadata = Map(
              "work_mode" -> request.workMode.toString,
              "location" -> request.location.getOrElse(""),
            ),
            createdAt = now,
          )
          _ <- timeRepo.logActivity(activity)
        } yield created

      override def endWorkSession(userId: PersonId): F[Option[EnhancedWorkSession]] =
        for {
          activeSessions <- timeRepo.getUserActiveSessions(userId)
          result <- activeSessions.headOption match {
            case None => None.pure[F]
            case Some(session) =>
              for {
                now <- Calendar[F].currentZonedDateTime
                nowLocal = now.toLocalDateTime

                totalMinutes = ChronoUnit.MINUTES.between(session.startTime, nowLocal).toInt
                productiveMinutes = Math.max(0, totalMinutes - session.breakMinutes)

                updated <- timeRepo.endWorkSession(
                  session.id,
                  nowLocal,
                  totalMinutes,
                  session.breakMinutes,
                  productiveMinutes,
                )

                // Log activity
                activityId <- ID.make[F, ActivityLogId]
                activity = ActivityLog(
                  id = activityId,
                  userId = userId,
                  activityType = ActivityType.SessionEnd,
                  timestamp = nowLocal,
                  metadata = Map(
                    "total_minutes" -> totalMinutes.toString,
                    "productive_minutes" -> productiveMinutes.toString,
                  ),
                  createdAt = now,
                )
                _ <- timeRepo.logActivity(activity)
              } yield updated
          }
        } yield result

      override def getCurrentSession(userId: PersonId): F[Option[EnhancedWorkSession]] =
        timeRepo.getUserActiveSessions(userId).map(_.headOption)

      override def getWorkSessions(
          userId: PersonId,
          limit: Int,
          page: Int,
        ): F[List[EnhancedWorkSession]] = {
        val offset = (page - 1) * limit
        timeRepo.getUserWorkSessions(userId, limit, offset)
      }

      override def startTimer(userId: PersonId, request: StartTimerRequest): F[TimeEntry] =
        for {
          entryId <- ID.make[F, TimeEntryId]
          now <- Calendar[F].currentZonedDateTime
          nowLocal = now.toLocalDateTime

          // Get current work session if any
          currentSession <- getCurrentSession(userId)

          entry = TimeEntry(
            id = entryId,
            userId = userId,
            taskId = request.taskId,
            workSessionId = currentSession.map(_.id),
            startTime = nowLocal,
            endTime = None,
            duration = None,
            description = request.description,
            isRunning = true,
            isBreak = false,
            breakReason = None,
            isManual = false,
            createdAt = now,
            updatedAt = now,
          )

          created <- timeRepo.startTimeEntry(entry)

          // Log activity
          activityId <- ID.make[F, ActivityLogId]
          activity = ActivityLog(
            id = activityId,
            userId = userId,
            activityType = ActivityType.TaskStart,
            timestamp = nowLocal,
            metadata = Map(
              "task_id" -> request.taskId.map(_.value.toString).getOrElse(""),
              "description" -> request.description,
            ),
            createdAt = now,
          )
          _ <- timeRepo.logActivity(activity)
        } yield created

      override def endTimer(userId: PersonId, entryId: TimeEntryId): F[Option[TimeEntry]] =
        for {
          entry <- timeRepo.findTimeEntryById(entryId)
          result <- entry.traverse { e =>
            if (e.userId != userId)
              AError
                .Forbidden("Timer entry does not belong to user")
                .raiseError[F, Option[TimeEntry]]
            else if (!e.isRunning)
              AError.BadRequest("Timer entry is not running").raiseError[F, Option[TimeEntry]]
            else
              for {
                now <- Calendar[F].currentZonedDateTime
                nowLocal = now.toLocalDateTime

                duration = ChronoUnit.MINUTES.between(e.startTime, nowLocal).toInt

                updated <- timeRepo.endTimeEntry(entryId, nowLocal, duration)

                // Log activity
                activityId <- ID.make[F, ActivityLogId]
                activity = ActivityLog(
                  id = activityId,
                  userId = userId,
                  activityType = ActivityType.TaskEnd,
                  timestamp = nowLocal,
                  metadata = Map(
                    "duration_minutes" -> duration.toString,
                    "task_id" -> e.taskId.map(_.value.toString).getOrElse(""),
                  ),
                  createdAt = now,
                )
                _ <- timeRepo.logActivity(activity)
              } yield updated
          }
        } yield result.flatten

      override def startBreak(userId: PersonId, request: StartBreakRequest): F[TimeEntry] =
        for {
          entryId <- ID.make[F, TimeEntryId]
          now <- Calendar[F].currentZonedDateTime
          nowLocal = now.toLocalDateTime

          currentSession <- getCurrentSession(userId)

          entry = TimeEntry(
            id = entryId,
            userId = userId,
            taskId = None,
            workSessionId = currentSession.map(_.id),
            startTime = nowLocal,
            endTime = None,
            duration = None,
            description = request.description.getOrElse(s"Break: ${request.reason}"),
            isRunning = true,
            isBreak = true,
            breakReason = Some(request.reason),
            isManual = false,
            createdAt = now,
            updatedAt = now,
          )

          created <- timeRepo.startTimeEntry(entry)

          // Log activity
          activityId <- ID.make[F, ActivityLogId]
          activity = ActivityLog(
            id = activityId,
            userId = userId,
            activityType = ActivityType.BreakStart,
            timestamp = nowLocal,
            metadata = Map(
              "break_reason" -> request.reason.toString,
              "description" -> request.description.getOrElse(""),
            ),
            createdAt = now,
          )
          _ <- timeRepo.logActivity(activity)
        } yield created

      override def endBreak(userId: PersonId): F[Option[TimeEntry]] =
        for {
          runningEntries <- timeRepo.getUserRunningTimeEntries(userId)
          breakEntry = runningEntries.find(_.isBreak)
          result <- breakEntry.traverse { entry =>
            for {
              now <- Calendar[F].currentZonedDateTime
              nowLocal = now.toLocalDateTime

              duration = ChronoUnit.MINUTES.between(entry.startTime, nowLocal).toInt

              updated <- timeRepo.endTimeEntry(entry.id, nowLocal, duration)

              // Update work session break minutes if applicable
              _ <- entry.workSessionId.traverse_ { sessionId =>
                timeRepo.findWorkSessionById(sessionId).flatMap {
                  case Some(session) =>
                    val newBreakMinutes = session.breakMinutes + duration
                    timeRepo
                      .endWorkSession(
                        sessionId,
                        session.endTime.getOrElse(nowLocal),
                        session.totalMinutes,
                        newBreakMinutes,
                        Math.max(0, session.totalMinutes - newBreakMinutes),
                      )
                      .void
                  case None => ().pure[F]
                }
              }

              // Log activity
              activityId <- ID.make[F, ActivityLogId]
              activity = ActivityLog(
                id = activityId,
                userId = userId,
                activityType = ActivityType.BreakEnd,
                timestamp = nowLocal,
                metadata = Map(
                  "duration_minutes" -> duration.toString,
                  "break_reason" -> entry.breakReason.map(_.toString).getOrElse(""),
                ),
                createdAt = now,
              )
              _ <- timeRepo.logActivity(activity)
            } yield updated
          }
        } yield result.flatten

      override def addManualEntry(userId: PersonId, request: ManualTimeEntryRequest): F[TimeEntry] =
        for {
          entryId <- ID.make[F, TimeEntryId]
          now <- Calendar[F].currentZonedDateTime

          entry = TimeEntry(
            id = entryId,
            userId = userId,
            taskId = request.taskId,
            workSessionId = None,
            startTime = request.startTime,
            endTime = Some(request.startTime.plusMinutes(request.durationMinutes.toLong)),
            duration = Some(request.durationMinutes),
            description = request.description,
            isRunning = false,
            isBreak = false,
            breakReason = None,
            isManual = true,
            createdAt = now,
            updatedAt = now,
          )

          created <- timeRepo.startTimeEntry(entry)
        } yield created

      override def getTimeEntries(userId: PersonId, taskId: Option[TaskId]): F[List[TimeEntry]] =
        timeRepo.getUserTimeEntries(userId, taskId)

      override def getRunningEntries(userId: PersonId): F[List[TimeEntry]] =
        timeRepo.getUserRunningTimeEntries(userId)

      override def getTimeDashboard(userId: PersonId): F[TimeDashboard] =
        for {
          currentSession <- getCurrentSession(userId)
          runningTimer <- getRunningEntries(userId).map(_.find(!_.isBreak))
          today <- Calendar[F].currentDate
          todayStats <- getDailyReport(userId, today)
          weekStart = today.minusDays(today.getDayOfWeek.getValue.toLong - 1)
          weekProgress <- getWeekProgress(userId, weekStart)
          recentActivities <- timeRepo.getUserActivityLogs(userId, 10, 0)
          productivity <- getProductivityMetrics(userId)
        } yield TimeDashboard(
          currentSession = currentSession,
          currentTimer = runningTimer,
          todayStats = todayStats,
          weekProgress = weekProgress,
          recentActivities = recentActivities,
          productivityScore = calculateProductivityScore(productivity),
        )

      override def getDailyReport(userId: PersonId, date: java.time.LocalDate): F[DailyTimeReport] =
        for {
          // Get all time entries for the date
          entries <- timeRepo.getUserTimeEntries(userId, None)
          dayEntries = entries.filter(_.startTime.toLocalDate == date)

          // Calculate metrics
          totalWorked = dayEntries.filterNot(_.isBreak).map(_.duration.getOrElse(0)).sum
          breakTime = dayEntries.filter(_.isBreak).map(_.duration.getOrElse(0)).sum
          productiveTime = Math.max(0, totalWorked - breakTime)
          tasksWorked = dayEntries.filter(_.taskId.isDefined).map(_.taskId).distinct.size

          // Get work mode from work session
          workSessions <- timeRepo.getUserWorkSessions(userId, 50, 0)
          daySession = workSessions.find(_.startTime.toLocalDate == date)

          startTime = dayEntries.map(_.startTime).minOption
          endTime = dayEntries.map(_.endTime).flatten.maxOption

        } yield DailyTimeReport(
          userId = userId,
          date = date,
          totalWorkedMinutes = totalWorked,
          productiveMinutes = productiveTime,
          breakMinutes = breakTime,
          tasksWorked = tasksWorked,
          workMode = daySession.map(_.workMode),
          startTime = startTime,
          endTime = endTime,
          overtimeMinutes = Math.max(0, totalWorked - 480), // 8 hours = 480 minutes
          isHoliday = false, // TODO: implement holiday detection
        )

      override def getWeeklyReport(
          userId: PersonId,
          weekStart: java.time.LocalDate,
        ): F[WeeklyTimeReport] =
        for {
          // Get daily reports for the week
          dailyReports <- (0 until 7).toList.traverse { dayOffset =>
            val date = weekStart.plusDays(dayOffset.toLong)
            getDailyReport(userId, date)
          }

          totalHours = dailyReports.map(_.totalWorkedMinutes).sum / 60.0
          productiveHours = dailyReports.map(_.productiveMinutes).sum / 60.0
          overtimeHours = dailyReports.map(_.overtimeMinutes).sum / 60.0
          workDays = dailyReports.count(_.totalWorkedMinutes > 0)
          avgDaily = if (workDays > 0) totalHours / workDays else 0.0

        } yield WeeklyTimeReport(
          userId = userId,
          weekStart = weekStart,
          totalWorkedHours = totalHours,
          productiveHours = productiveHours,
          overtimeHours = overtimeHours,
          workDays = workDays,
          averageDailyHours = avgDaily,
          dailyReports = dailyReports,
        )

      override def getProductivityMetrics(userId: PersonId): F[ProductivityMetrics] =
        for {
          recentEntries <- timeRepo.getUserTimeEntries(userId, None)
          last30Days = recentEntries.filter(_.startTime.isAfter(LocalDateTime.now().minusDays(30)))

          focusTime = last30Days.filterNot(_.isBreak).map(_.duration.getOrElse(0)).sum / 60.0
          avgSessionLength = if (last30Days.nonEmpty) focusTime / last30Days.size else 0.0
          breakFreq =
            if (last30Days.nonEmpty)
              last30Days.count(_.isBreak).toDouble / last30Days.filterNot(_.isBreak).size
            else 0.0

          taskSwitches = last30Days.sliding(2).count {
            case List(prev, curr) => prev.taskId != curr.taskId
            case _ => false
          }

          timePerTask =
            if (last30Days.map(_.taskId).distinct.size > 0)
              focusTime / last30Days.map(_.taskId).distinct.size
            else 0.0

        } yield ProductivityMetrics(
          focusTimeHours = focusTime,
          averageSessionLength = avgSessionLength,
          breakFrequency = breakFreq,
          peakProductivityHours = List(9, 10, 14, 15), // TODO: calculate from actual data
          taskSwitchCount = taskSwitches,
          timePerTask = timePerTask,
        )

      private def getWeekProgress(
          userId: PersonId,
          weekStart: java.time.LocalDate,
        ): F[WeekProgress] =
        for {
          weeklyReport <- getWeeklyReport(userId, weekStart)
          targetHours = 40.0 // 40 hours per week
          workedHours = weeklyReport.totalWorkedHours
          remaining = Math.max(0, targetHours - workedHours)
          progress = Math.min(100.0, (workedHours / targetHours) * 100)

          today <- Calendar[F].currentDate
          dailyBreakdown <- weeklyReport.dailyReports.traverse { daily =>
            DailyProgress(
              date = daily.date,
              workedHours = daily.totalWorkedMinutes / 60.0,
              targetHours = 8.0,
              isToday = daily.date == today,
            ).pure[F]
          }
        } yield WeekProgress(
          weekStart = weekStart,
          targetHours = targetHours,
          workedHours = workedHours,
          remainingHours = remaining,
          progressPercentage = progress,
          dailyBreakdown = dailyBreakdown,
        )

      private def calculateProductivityScore(metrics: ProductivityMetrics): Double = {
        // Simple productivity score calculation
        val focusScore = Math.min(8.0, metrics.focusTimeHours) / 8.0 * 40 // 40 points for 8+ hours focus
        val sessionScore = Math.min(2.0, metrics.averageSessionLength) / 2.0 * 30 // 30 points for 2+ hour sessions
        val breakScore =
          if (metrics.breakFrequency > 0.1 && metrics.breakFrequency < 0.3) 20 else 10 // 20 points for good break frequency
        val taskScore = Math.max(0, 10 - metrics.taskSwitchCount) // Fewer task switches = higher score

        Math.min(100.0, focusScore + sessionScore + breakScore + taskScore)
      }
    }
}
