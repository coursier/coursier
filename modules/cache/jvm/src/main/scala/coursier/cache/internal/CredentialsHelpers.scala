package coursier.cache.internal

import java.net.URI

import coursier.core.Authentication

abstract class CredentialsHelpers {

  def host: String
  def username: String
  def password: String

  def matches(url: String): Boolean = {
    val uri = new URI(url)
    val scheme = uri.getScheme
    val host0 = uri.getHost
    (scheme == "http" || scheme == "https") && host == host0
  }

  def authentication: Authentication =
    Authentication(username, password)

}
