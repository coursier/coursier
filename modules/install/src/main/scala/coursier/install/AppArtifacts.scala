package coursier.install

import java.nio.file.Path

import coursier.Fetch
import coursier.util.Artifact
import dataclass.data

@data class AppArtifacts(
  fetchResult: Fetch.Result = Fetch.Result(),
  shared: Seq[(Artifact, Path)] = Nil,
  extraProperties: Seq[(String, String)] = Nil,
  platformSuffixOpt: Option[String] = None
)

object AppArtifacts {
  def empty: AppArtifacts =
    AppArtifacts()
}
