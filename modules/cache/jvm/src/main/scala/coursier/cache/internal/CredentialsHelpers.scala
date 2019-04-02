package coursier.cache.internal

import java.net.URI

import coursier.core.Authentication

abstract class CredentialsHelpers {

  def realm: Option[String]
  def host: String
  def username: String
  def password: String
  def optional: Boolean

  def matches(url: String, realm0: Option[String]): Boolean = {
    val uri = new URI(url)
    val scheme = uri.getScheme
    val host0 = uri.getHost
    (scheme == "http" || scheme == "https") && host == host0 && realm.forall(realm0.contains)
  }

  def authentication: Authentication =
    Authentication(
      username,
      password,
      realmOpt = realm,
      optional = optional
    )

}
