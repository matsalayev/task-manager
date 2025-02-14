import com.typesafe.sbt.packager.Keys.*
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker.DockerChmodType
import com.typesafe.sbt.packager.docker.DockerPlugin
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerChmodType
import sbt.*
import sbt.Keys.*

object DockerImagePlugin extends AutoPlugin {
  val dockerRepositoryName: Option[String] = sys.env.get("AWS_ECR_REPOSITORY")

  object autoImport {
    lazy val generateServiceImage: TaskKey[Unit] =
      taskKey[Unit]("Generates an image with the native binary")
    lazy val CompileAndTest = "compile->compile;test->test"
  }
  override def projectSettings: Seq[Def.Setting[?]] =
    Seq(
      dockerBaseImage    := "openjdk",
      dockerRepository   := dockerRepositoryName,
      dockerUpdateLatest := true,
      dockerChmodType    := DockerChmodType.UserGroupWriteExecute,
    )

  def serviceSetting(serviceName: String): Seq[Def.Setting[?]] =
    Seq(
      Docker / packageName         := "task-manager/backend",
      packageDoc / publishArtifact := false,
      packageSrc / publishArtifact := true,
      publish / skip               := false,
    )

  override def requires: sbt.Plugins =
    JavaAppPackaging && DockerPlugin
}
