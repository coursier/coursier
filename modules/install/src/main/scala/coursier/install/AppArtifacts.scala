package coursier.install

import java.io.File

import coursier.Fetch
import coursier.parse.JavaOrScalaDependency
import coursier.util.Artifact
import dataclass.data

@data class AppArtifacts(
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

  final class ScalaDependenciesNotFound(val scalaDependencies: Seq[JavaOrScalaDependency.ScalaDependency])
    extends AppArtifactsException(s"Can't find a scala version suffix for ${scalaDependencies.map(_.repr).mkString(", ")} (likely a non existing module or version)")
}
