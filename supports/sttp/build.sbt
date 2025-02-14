import Dependencies.*

libraryDependencies ++=
  com.softwaremill.sttp.all ++
    Seq(
      org.typelevel.log4cats,
      ch.qos.logback,
    )
