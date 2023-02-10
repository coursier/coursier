package coursier.maven

import coursier.core.{Authentication, Module, Repository}
import coursier.util.Artifact

trait MavenRepositoryLike extends Repository {

  def root: String
  def authentication: Option[Authentication]
  def versionsCheckHasModule: Boolean

  def withRoot(root: String): MavenRepositoryLike
  def withAuthentication(authentication: Option[Authentication]): MavenRepositoryLike
  def withVersionsCheckHasModule(versionsCheckHasModule: Boolean): MavenRepositoryLike

  // Methods below are mainly used by MavenComplete

  def urlFor(path: Seq[String], isDir: Boolean = false): String
  def artifactFor(url: String, changing: Boolean): Artifact
  def moduleDirectory(module: Module): String
}
