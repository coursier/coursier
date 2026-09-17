package coursier.maven.internal

import coursier.core.Authentication
import coursier.maven.SbtMavenRepository

trait SbtMavenRepositoryHelpers { self: SbtMavenRepository =>
  def withAuthentication(authentication: Option[Authentication]): SbtMavenRepository =
    copy(authentication = authentication)
  def withRoot(root: String): SbtMavenRepository =
    copy(root = root)
  def withVersionsCheckHasModule(versionsCheckHasModule: Boolean): SbtMavenRepository =
    copy(versionsCheckHasModule = versionsCheckHasModule)
  def withCheckModule(checkModule: Boolean): SbtMavenRepository =
    copy(checkModule = checkModule)
}
