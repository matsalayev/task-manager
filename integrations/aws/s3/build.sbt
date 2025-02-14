name := "s3"

libraryDependencies ++=
  Dependencies.com.s3.all ++
    Dependencies.co.fs2.all ++
    Seq(
      Dependencies.com.guava
    )

dependsOn(LocalProject("common"))
