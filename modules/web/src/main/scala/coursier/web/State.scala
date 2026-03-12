package coursier.web

import coursier.core.{Dependency, Resolution}
import coursier.maven.MavenRepository

final case class State(
  modules: Seq[Dependency],
  repositories: Seq[(String, MavenRepository)],
  options: ResolutionOptions,
  resolutionOpt: Option[Resolution],
  editModuleIdx: Int,
  editRepoIdx: Int,
  resolving: Boolean,
  reverseTree: Boolean,
  log: Seq[String]
)
