import Dependencies.*
import DockerImagePlugin.autoImport.CompileAndTest
import sbt.LocalProject

name := "endpoints"

lazy val `endpoints-domain` = project
  .in(file("00-domain"))
  .dependsOn(
    LocalProject("support_logback"),
    LocalProject("support_services"),
    LocalProject("common"),
    LocalProject("test-tools") % CompileAndTest,
  )

lazy val `endpoints-repos` =
  project
    .in(file("01-repos"))
    .dependsOn(
      `endpoints-domain`               % CompileAndTest,
      LocalProject("support_database") % CompileAndTest,
    )

lazy val `endpoints-core` =
  project
    .in(file("02-core"))
    .dependsOn(
      `endpoints-repos`,
      LocalProject("support_redis"),
      LocalProject("integration_aws-s3"),
      LocalProject("integration_telegram"),
    )
    .settings(
      libraryDependencies ++= Seq(
        dev.profunktor.`http4s-jwt-auth`
      )
    )

lazy val `endpoints-jobs` =
  project
    .in(file("03-jobs"))
    .dependsOn(
      `endpoints-core`,
      LocalProject("support_jobs"),
    )

lazy val `endpoints-api` =
  project
    .in(file("03-api"))
    .dependsOn(
      `endpoints-core`
    )

lazy val `endpoints-server` =
  project
    .in(file("04-server"))
    .dependsOn(`endpoints-api`)

lazy val `endpoints-runner` =
  project
    .in(file("05-runner"))
    .dependsOn(`endpoints-server`, `endpoints-jobs`)
    .settings(DockerImagePlugin.serviceSetting("enrollment"))
    .enablePlugins(DockerImagePlugin, JavaAppPackaging, DockerPlugin)

aggregateProjects(
  `endpoints-domain`,
  `endpoints-repos`,
  `endpoints-core`,
  `endpoints-jobs`,
  `endpoints-api`,
  `endpoints-server`,
  `endpoints-runner`,
)
