package tm.endpoint.routes

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

import cats.effect.IO
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import weaver.SimpleIOSuite

import tm.domain.PersonId
import tm.domain.TaskId
import tm.domain.WorkId
import tm.domain.auth.AuthedUser
import tm.domain.time._
import tm.services.TimeTrackingService
import tm.syntax.refined._

object TimeTrackingRoutesSpec extends SimpleIOSuite {

  // Mock TimeTrackingService for testing
  def mockTimeTrackingService: TimeTrackingService[IO] = new TimeTrackingService[IO] {
    override def startWorkSession(
        userId: PersonId,
        request: StartWorkSessionRequest,
      ): IO[EnhancedWorkSession] =
      IO.pure(
        EnhancedWorkSession(
          id = WorkId(UUID.randomUUID()),
          userId = userId,
          startTime = LocalDateTime.now(),
          endTime = None,
          workMode = request.workMode,
          isRunning = true,
          totalMinutes = 0,
          breakMinutes = 0,
          productiveMinutes = 0,
          description = request.description,
          location = request.location,
          createdAt = java.time.ZonedDateTime.now(),
        )
      )

    override def endWorkSession(userId: PersonId): IO[Option[EnhancedWorkSession]] =
      IO.pure(
        Some(
          EnhancedWorkSession(
            id = WorkId(UUID.randomUUID()),
            userId = userId,
            startTime = LocalDateTime.now().minusHours(8),
            endTime = Some(LocalDateTime.now()),
            workMode = WorkMode.Office,
            isRunning = false,
            totalMinutes = 480,
            breakMinutes = 60,
            productiveMinutes = 420,
            description = None,
            location = None,
            createdAt = java.time.ZonedDateTime.now(),
          )
        )
      )

    override def getCurrentSession(userId: PersonId): IO[Option[EnhancedWorkSession]] =
      IO.pure(
        Some(
          EnhancedWorkSession(
            id = WorkId(UUID.randomUUID()),
            userId = userId,
            startTime = LocalDateTime.now().minusHours(2),
            endTime = None,
            workMode = WorkMode.Office,
            isRunning = true,
            totalMinutes = 0,
            breakMinutes = 0,
            productiveMinutes = 0,
            description = None,
            location = None,
            createdAt = java.time.ZonedDateTime.now(),
          )
        )
      )

    override def getWorkSessions(
        userId: PersonId,
        limit: Int,
        page: Int,
      ): IO[List[EnhancedWorkSession]] =
      IO.pure(
        List(
          EnhancedWorkSession(
            id = WorkId(UUID.randomUUID()),
            userId = userId,
            startTime = LocalDateTime.now().minusHours(8),
            endTime = Some(LocalDateTime.now()),
            workMode = WorkMode.Office,
            isRunning = false,
            totalMinutes = 480,
            breakMinutes = 60,
            productiveMinutes = 420,
            description = None,
            location = None,
            createdAt = java.time.ZonedDateTime.now(),
          )
        )
      )

    override def startTimer(userId: PersonId, request: StartTimerRequest): IO[TimeEntry] =
      IO.pure(
        TimeEntry(
          id = TimeEntryId(UUID.randomUUID()),
          userId = userId,
          taskId = request.taskId,
          workSessionId = None,
          startTime = LocalDateTime.now(),
          endTime = None,
          duration = None,
          description = request.description,
          isRunning = true,
          isBreak = false,
          breakReason = None,
          isManual = false,
          createdAt = java.time.ZonedDateTime.now(),
          updatedAt = java.time.ZonedDateTime.now(),
        )
      )

    override def endTimer(userId: PersonId, entryId: TimeEntryId): IO[Option[TimeEntry]] =
      IO.pure(
        Some(
          TimeEntry(
            id = entryId,
            userId = userId,
            taskId = None,
            workSessionId = None,
            startTime = LocalDateTime.now().minusHours(2),
            endTime = Some(LocalDateTime.now()),
            duration = Some(120),
            description = "Test entry",
            isRunning = false,
            isBreak = false,
            breakReason = None,
            isManual = false,
            createdAt = java.time.ZonedDateTime.now(),
            updatedAt = java.time.ZonedDateTime.now(),
          )
        )
      )

    override def startBreak(userId: PersonId, request: StartBreakRequest): IO[TimeEntry] =
      IO.pure(
        TimeEntry(
          id = TimeEntryId(UUID.randomUUID()),
          userId = userId,
          taskId = None,
          workSessionId = None,
          startTime = LocalDateTime.now(),
          endTime = None,
          duration = None,
          description = request.description.getOrElse("Break"),
          isRunning = true,
          isBreak = true,
          breakReason = Some(request.reason),
          isManual = false,
          createdAt = java.time.ZonedDateTime.now(),
          updatedAt = java.time.ZonedDateTime.now(),
        )
      )

    override def endBreak(userId: PersonId): IO[Option[TimeEntry]] =
      IO.pure(
        Some(
          TimeEntry(
            id = TimeEntryId(UUID.randomUUID()),
            userId = userId,
            taskId = None,
            workSessionId = None,
            startTime = LocalDateTime.now().minusMinutes(15),
            endTime = Some(LocalDateTime.now()),
            duration = Some(15),
            description = "Break",
            isRunning = false,
            isBreak = true,
            breakReason = Some(BreakReason.Coffee),
            isManual = false,
            createdAt = java.time.ZonedDateTime.now(),
            updatedAt = java.time.ZonedDateTime.now(),
          )
        )
      )

    override def addManualEntry(
        userId: PersonId,
        request: ManualTimeEntryRequest,
      ): IO[TimeEntry] =
      IO.pure(
        TimeEntry(
          id = TimeEntryId(UUID.randomUUID()),
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
          createdAt = java.time.ZonedDateTime.now(),
          updatedAt = java.time.ZonedDateTime.now(),
        )
      )

    override def getTimeEntries(
        userId: PersonId,
        taskId: Option[TaskId],
      ): IO[List[TimeEntry]] =
      IO.pure(
        List(
          TimeEntry(
            id = TimeEntryId(UUID.randomUUID()),
            userId = userId,
            taskId = taskId,
            workSessionId = None,
            startTime = LocalDateTime.now().minusHours(2),
            endTime = Some(LocalDateTime.now()),
            duration = Some(120),
            description = "Test entry",
            isRunning = false,
            isBreak = false,
            breakReason = None,
            isManual = false,
            createdAt = java.time.ZonedDateTime.now(),
            updatedAt = java.time.ZonedDateTime.now(),
          )
        )
      )

    override def getRunningEntries(userId: PersonId): IO[List[TimeEntry]] =
      IO.pure(
        List(
          TimeEntry(
            id = TimeEntryId(UUID.randomUUID()),
            userId = userId,
            taskId = None,
            workSessionId = None,
            startTime = LocalDateTime.now().minusHours(1),
            endTime = None,
            duration = None,
            description = "Running entry",
            isRunning = true,
            isBreak = false,
            breakReason = None,
            isManual = false,
            createdAt = java.time.ZonedDateTime.now(),
            updatedAt = java.time.ZonedDateTime.now(),
          )
        )
      )

    override def getTimeDashboard(userId: PersonId): IO[TimeDashboard] =
      IO.pure(
        TimeDashboard(
          currentSession = None,
          currentTimer = None,
          todayStats = DailyTimeReport(
            userId = userId,
            date = LocalDate.now(),
            totalWorkedMinutes = 480,
            productiveMinutes = 420,
            breakMinutes = 60,
            tasksWorked = 5,
            workMode = Some(WorkMode.Office),
            startTime = Some(LocalDateTime.now().minusHours(8)),
            endTime = Some(LocalDateTime.now()),
            overtimeMinutes = 0,
            isHoliday = false,
          ),
          weekProgress = WeekProgress(
            weekStart = LocalDate.now().minusDays(6),
            targetHours = 40.0,
            workedHours = 35.0,
            remainingHours = 5.0,
            progressPercentage = 87.5,
            dailyBreakdown = List.empty,
          ),
          recentActivities = List.empty,
          productivityScore = 87.5,
        )
      )

    override def getDailyReport(userId: PersonId, date: LocalDate): IO[DailyTimeReport] =
      IO.pure(
        DailyTimeReport(
          userId = userId,
          date = date,
          totalWorkedMinutes = 480,
          productiveMinutes = 420,
          breakMinutes = 60,
          tasksWorked = 5,
          workMode = Some(WorkMode.Office),
          startTime = Some(LocalDateTime.now().minusHours(8)),
          endTime = Some(LocalDateTime.now()),
          overtimeMinutes = 0,
          isHoliday = false,
        )
      )

    override def getWeeklyReport(userId: PersonId, weekStart: LocalDate): IO[WeeklyTimeReport] =
      IO.pure(
        WeeklyTimeReport(
          userId = userId,
          weekStart = weekStart,
          totalWorkedHours = 40.0,
          productiveHours = 35.0,
          overtimeHours = 0.0,
          workDays = 5,
          averageDailyHours = 8.0,
          dailyReports = List.empty,
        )
      )

    override def getProductivityMetrics(userId: PersonId): IO[ProductivityMetrics] =
      IO.pure(
        ProductivityMetrics(
          focusTimeHours = 35.0,
          averageSessionLength = 4.0,
          breakFrequency = 0.2,
          peakProductivityHours = List(9, 10, 14, 15),
          taskSwitchCount = 8,
          timePerTask = 120.0,
        )
      )
  }

  implicit val logger: org.typelevel.log4cats.Logger[IO] =
    org.typelevel.log4cats.noop.NoOpLogger.impl[IO]

  def createAuthenticatedUser: AuthedUser = AuthedUser.User(
    id = PersonId(UUID.randomUUID()),
    corporateId = tm.domain.CorporateId(UUID.randomUUID()),
    role = tm.domain.enums.Role.Employee,
    phone = "+998901234567",
  )

  test("POST /time/sessions/start should start a work session") {
    val routes = TimeTrackingRoutes[IO](mockTimeTrackingService)
    val user = createAuthenticatedUser
    val request = StartWorkSessionRequest(
      workMode = WorkMode.Remote,
      location = Some("Home"),
      description = Some("Remote work session"),
    )

    val authedRequest = AuthedRequest(
      user,
      Request[IO](
        method = Method.POST,
        uri = uri"/sessions/start",
      ).withEntity(request),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Ok)
  }

  test("POST /time/sessions/end should end current work session") {
    val routes = TimeTrackingRoutes[IO](mockTimeTrackingService)
    val user = createAuthenticatedUser

    val authedRequest = AuthedRequest(
      user,
      Request[IO](
        method = Method.POST,
        uri = uri"/sessions/end",
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Ok)
  }

  test("GET /time/sessions/current should return current session") {
    val routes = TimeTrackingRoutes[IO](mockTimeTrackingService)
    val user = createAuthenticatedUser

    val authedRequest = AuthedRequest(
      user,
      Request[IO](
        method = Method.GET,
        uri = uri"/sessions/current",
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Ok)
  }

  test("POST /time/entries/start should start a timer") {
    val routes = TimeTrackingRoutes[IO](mockTimeTrackingService)
    val user = createAuthenticatedUser
    val request = StartTimerRequest(
      taskId = Some(TaskId(UUID.randomUUID())),
      description = "Time entry for task",
    )

    val authedRequest = AuthedRequest(
      user,
      Request[IO](
        method = Method.POST,
        uri = uri"/entries/start",
      ).withEntity(request),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Ok)
  }

  test("GET /time/dashboard should return time dashboard") {
    val routes = TimeTrackingRoutes[IO](mockTimeTrackingService)
    val user = createAuthenticatedUser

    val authedRequest = AuthedRequest(
      user,
      Request[IO](
        method = Method.GET,
        uri = uri"/dashboard",
      ),
    )

    for {
      response <- routes.`private`.orNotFound.run(authedRequest)
    } yield expect(response.status == Status.Ok)
  }
}
