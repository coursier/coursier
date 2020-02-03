package coursier.install

import coursier.core.{Classifier, Repository, Type}
import coursier.parse.{JavaOrScalaDependency, JavaOrScalaModule}
import dataclass._

@data class AppDescriptor(
  repositories: Seq[Repository] = Nil,
  dependencies: Seq[JavaOrScalaDependency] = Nil,
  sharedDependencies: Seq[JavaOrScalaModule] = Nil,
  launcherType: LauncherType = LauncherType.Bootstrap,
  classifiers: Set[Classifier] = Set.empty,
  mainArtifacts: Boolean = true,
  artifactTypes: Set[Type] = Set.empty,
  mainClass: Option[String] = None,
  defaultMainClass: Option[String] = None,
  javaOptions: Seq[String] = Nil,
  javaProperties: Seq[(String, String)] = Nil,
  scalaVersionOpt: Option[String] = None,
  nameOpt: Option[String] = None,
  graalvmOptions: Option[AppDescriptor.GraalvmOptions] = None
) {
  def overrideVersion(ver: String): AppDescriptor =
    withDependencies {
      if (dependencies.isEmpty)
        dependencies
      else {
        val dep = dependencies.head.withUnderlyingDependency(_.withVersion(ver))
        dep +: dependencies.tail
      }
    }
}

object AppDescriptor {

  @data class GraalvmOptions(
    options: Seq[String] = Nil
  )

}
