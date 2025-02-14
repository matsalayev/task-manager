import Dependencies.Jobs

name         := "jobs"
scalaVersion := "2.13.11"

libraryDependencies ++= Seq(
  Jobs.Cron4s.core,
  Jobs.Fs2Cron4s.core,
)

dependsOn(LocalProject("common"))
