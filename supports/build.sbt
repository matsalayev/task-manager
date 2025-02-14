name := "supports"

lazy val support_services = project.in(file("services"))
lazy val support_logback = project.in(file("logback"))
lazy val support_database = project.in(file("database"))
lazy val support_redis = project.in(file("redis"))
lazy val support_sttp = project.in(file("sttp"))
lazy val support_jobs = project.in(file("jobs"))

aggregateProjects(
  support_services,
  support_logback,
  support_database,
  support_redis,
  support_sttp,
  support_jobs,
)
