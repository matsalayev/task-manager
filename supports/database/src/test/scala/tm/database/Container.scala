package tm.database

import java.time.ZoneId

import cats.effect.IO
import cats.effect.Resource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.scalacheck.CheckConfig

import tm.support.database.Migrations
import tm.support.database.MigrationsConfig

trait Container {
  def schemaName: String
  def migrationLocation: Option[String] = None

  type Res
  lazy val imageName: String = "postgres:16"
  lazy val container: PostgreSQLContainer[Nothing] = new PostgreSQLContainer(
    DockerImageName
      .parse(imageName)
      .asCompatibleSubstituteFor("postgres")
  )

  val customCheckConfig: CheckConfig = CheckConfig.default.copy(minimumSuccessful = 20)

  implicit val logger: SelfAwareStructuredLogger[IO] = NoOpLogger[IO]
//    implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  val dbResource: Resource[IO, PostgreSQLContainer[Nothing]] =
    for {
      container <- Resource.fromAutoCloseable {
        IO.blocking {
          container.setCommand("postgres", "-c", "max_connections=150")
          container.addEnv("TZ", ZoneId.systemDefault().getId)
          container.start()
          container
        }
      }
      _ <- Resource.eval(logger.info("Container has started"))
      migrationConfig = MigrationsConfig(
        hostname = container.getHost,
        port = container.getFirstMappedPort,
        database = container.getDatabaseName,
        username = container.getUsername,
        password = container.getPassword,
        schema = schemaName,
        location = migrationLocation.getOrElse("db/migration"),
      )
      _ <- Resource.eval(logger.info(s"Migrating database at ${container.getJdbcUrl}"))
      _ <- Resource.eval(Migrations.run[IO](migrationConfig))
    } yield container
}
