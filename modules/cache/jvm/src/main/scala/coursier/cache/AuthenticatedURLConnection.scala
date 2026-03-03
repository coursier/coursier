package coursier.cache

import coursier.core.Authentication

import java.net.URLConnection

trait AuthenticatedURLConnection extends URLConnection {
  def authenticate(authentication: Authentication): Unit
}
