package coursier.cache

import java.net.{Proxy, URLConnection}
import javax.net.ssl.{HostnameVerifier, SSLSocketFactory}

import coursier.core.Authentication
import coursier.credentials.DirectCredentials
import dataclass.{data, since}

import scala.concurrent.duration.FiniteDuration

// format: off
@data(deprecatedSetters = true, deprecatedSettersMessage = "Use copy instead", deprecatedSettersSince = "2.1.25") case class ConnectionBuilder(
  url: String,
  authentication: Option[Authentication] = None,
  alreadyDownloaded: Long = 0L,
  followHttpToHttpsRedirections: Boolean = false,
  followHttpsToHttpRedirections: Boolean = false,
  autoCredentials: Seq[DirectCredentials] = Nil,
  sslSocketFactoryOpt: Option[SSLSocketFactory] = None,
  hostnameVerifierOpt: Option[HostnameVerifier] = None,
  method: String = "GET",
  maxRedirectionsOpt: Option[Int] = Some(20),
  proxy: Option[Proxy] = None,
  @since
    classLoaders: Seq[ClassLoader] = Nil,
  @since
    connectTimeout: Option[FiniteDuration] = CacheDefaults.connectTimeout,
    readTimeout: Option[FiniteDuration] = CacheDefaults.readTimeout,
  @since
    userAgentOpt: Option[String] = None,
  @since
    authRealmOpt: Option[String] = None
) {
  // format: on

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
      proxy,
      method,
      authRealmOpt,
      redirectionCount = 0,
      maxRedirectionsOpt,
      classLoaders,
      connectTimeout,
      readTimeout,
      userAgentOpt
    ))
}
