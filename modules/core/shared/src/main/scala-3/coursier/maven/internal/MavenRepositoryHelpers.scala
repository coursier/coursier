package coursier.maven.internal

import coursier.core.Authentication
import coursier.maven.MavenRepository

trait MavenRepositoryHelpers { self: MavenRepository =>
  def withAuthentication(authentication: Option[Authentication]): MavenRepository =
    copy(authentication = authentication)
  def withRoot(root: String): MavenRepository =
    copy(root = root)
  def withVersionsCheckHasModule(versionsCheckHasModule: Boolean): MavenRepository =
    copy(versionsCheckHasModule = versionsCheckHasModule)

  def withCheckModule(checkModule: Boolean): MavenRepository =
    copy(checkModule = checkModule)
}
