package coursier.install

import dataclass.data

import java.io.File

import coursier.Fetch
import coursier.parse.JavaOrScalaDependency
import coursier.util.Artifact

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class AppArtifacts(
  fetchResult: Fetch.Result = Fetch.Result(),
  shared: Seq[(Artifact, File)] = Nil,
  extraProperties: Seq[(String, String)] = Nil,
  platformSuffixOpt: Option[String] = None
)

object AppArtifacts {
  def empty: AppArtifacts =
    AppArtifacts()

  sealed abstract class AppArtifactsException(message: String, parent: Throwable = null)
      extends Exception(message, parent)

  final class ScalaDependenciesNotFound(
    val scalaDependencies: Seq[JavaOrScalaDependency.ScalaDependency]
  ) extends AppArtifactsException(
        s"Can't find a scala version suffix for ${scalaDependencies.map(_.repr).mkString(", ")} (likely a non existing module or version)"
      )

  /** Raised when none of the repositories could list the available Scala (or Scala platform)
    * versions, so that no Scala version suffix can be tried for the dependencies
    *
    * @param versionsOf
    *   what we tried to list the versions of, like "Scala" or "Scala Native"
    * @param errors
    *   the errors of each repository we tried to list versions from
    */
  final class CannotListScalaVersions(
    val scalaDependencies: Seq[JavaOrScalaDependency.ScalaDependency],
    val versionsOf: String,
    val errors: Seq[String]
  ) extends AppArtifactsException(
        s"Can't find a scala version suffix for ${scalaDependencies.map(_.repr).mkString(", ")}: " +
          s"could not list the available $versionsOf versions" +
          errors.map("\n  " + _).mkString
      )
}
