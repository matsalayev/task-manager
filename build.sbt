import Dependencies.*

ThisBuild / version := sys.env.getOrElse("BUILD_VERSION", "latest")

ThisBuild / scalaVersion := "2.13.11"

lazy val root = project
  .in(file("."))
  .settings(
    name := "task-manager"
  )
  .aggregate(
    endpoints,
    supports,
    `test-tools`,
  )

lazy val common =
  project
    .in(file("common"))
    .settings(
      name := "common"
    )
    .settings(
      libraryDependencies ++=
        Dependencies.io.circe.all ++
          eu.timepit.refined.all ++
          com.github.pureconfig.all ++
          com.beachape.enumeratum.all ++
          tf.tofu.derevo.all ++
          Seq(
            org.typelevel.cats.core,
            javax.xml.bind,
            org.typelevel.cats.effect,
            org.typelevel.log4cats,
            ch.qos.logback,
            dev.optics.monocle,
            Dependencies.io.estatico.newtype,
            Dependencies.io.github.jmcardon.`tsec-password`,
            Dependencies.io.scalaland.chimney,
            Dependencies.org.openpdf.core,
          )
    )

lazy val integrations = project
  .in(file("integrations"))
  .settings(
    name := "integrations"
  )

lazy val supports = project
  .in(file("supports"))
  .settings(
    name := "supports"
  )

lazy val endpoints = project
  .in(file("endpoints"))
  .settings(
    name := "endpoints"
  )

lazy val `test-tools` = project
  .in(file("test-tools"))
  .settings(
    name := "test-tools",
    libraryDependencies ++=
      tf.tofu.derevo.all ++ Dependencies.com.disneystreaming.all,
  )
  .dependsOn(common)

Global / lintUnusedKeysOnLoad := false
Global / onChangedBuildSource := ReloadOnSourceChanges

val runServer = inputKey[Unit]("Runs server")

runServer := {
  (LocalProject("endpoints-runner") / Compile / run).evaluated
}
