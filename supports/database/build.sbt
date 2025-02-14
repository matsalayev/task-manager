import Dependencies.*
import DockerImagePlugin.autoImport.CompileAndTest

name := "database"

dependsOn(LocalProject("common"), LocalProject("test-tools") % CompileAndTest)
libraryDependencies ++=
  org.tpolecat.skunk.all ++
    Seq(
      org.flywaydb.core       % CompileAndTest,
      org.flywaydb.postgresql % CompileAndTest,
      org.postgresql,
      org.testcontainers,
    )
