package coursier.cache

import java.net.URLConnection
import javax.net.ssl.{HostnameVerifier, SSLSocketFactory}

import coursier.core.Authentication
import coursier.credentials.DirectCredentials
import dataclass.data

@data class ConnectionBuilder(
  url: String,
  authentication: Option[Authentication] = None,
  alreadyDownloaded: Long = 0L,
  followHttpToHttpsRedirections: Boolean = false,
  followHttpsToHttpRedirections: Boolean = false,
  autoCredentials: Seq[DirectCredentials] = Nil,
  sslSocketFactoryOpt: Option[SSLSocketFactory] = None,
  hostnameVerifierOpt: Option[HostnameVerifier] = None,
  method: String = "GET",
  maxRedirectionsOpt: Option[Int] = Some(20)
) {

  def connection(): URLConnection = {
    val (c, partial) = connectionMaybePartial()
    assert(!partial)
    c
  }

  def connectionMaybePartial(): (URLConnection, Boolean) =
    CacheUrl.urlConnectionMaybePartial(CacheUrl.Args(
      url,
      url,
      authentication,
      alreadyDownloaded,
      followHttpToHttpsRedirections,
      followHttpsToHttpRedirections,
      autoCredentials,
      sslSocketFactoryOpt,
      hostnameVerifierOpt,
      method,
      None,
      redirectionCount = 0,
      maxRedirectionsOpt
    ))
}

