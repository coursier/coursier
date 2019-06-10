package coursier.cli.app

import coursier.core.{Classifier, Repository, Type}
import coursier.parse.{JavaOrScalaDependency, JavaOrScalaModule}

final case class AppDescriptor(
  repositories: Seq[Repository],
  dependencies: Seq[JavaOrScalaDependency],
  sharedDependencies: Seq[JavaOrScalaModule],
  launcherType: LauncherType,
  classifiers: Set[Classifier],
  mainArtifacts: Boolean,
  artifactTypes: Set[Type],
  mainClass: Option[String],
  defaultMainClass: Option[String],
  javaOptions: Seq[String],
  javaProperties: Seq[(String, String)],
  scalaVersionOpt: Option[String],
  nameOpt: Option[String],
  graalvmOptions: Option[AppDescriptor.GraalvmOptions]
) {
  def overrideVersion(ver: String): AppDescriptor =
    copy(
      dependencies = {
        if (dependencies.isEmpty)
          dependencies
        else {
          val dep = dependencies.head.withUnderlyingDependency(dep => dep.copy(version = ver))
          dep +: dependencies.tail
        }
      }
    )
}

object AppDescriptor {

  final case class GraalvmOptions(
    options: Seq[String],
    reflectionConf: Option[String],
    shellPrependOptions: Seq[String]
  )

}
