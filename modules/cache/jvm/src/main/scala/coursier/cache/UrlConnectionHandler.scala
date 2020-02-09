package coursier.cache

import java.net.URLConnection
import javax.net.ssl.{HostnameVerifier, SSLSocketFactory}

import coursier.core.Authentication
import coursier.credentials.DirectCredentials

trait UrlConnectionHandler {

  final def urlConnection(
    url0: String,
    authentication: Option[Authentication]
  ): URLConnection =
    urlConnection(
      url0,
      authentication,
      followHttpToHttpsRedirections = false,
      followHttpsToHttpRedirections = false,
      credentials = Nil,
      sslSocketFactoryOpt = None,
      hostnameVerifierOpt = None,
      method = "GET",
      maxRedirectionsOpt = Some(20)
    )

  def urlConnection(
    url0: String,
    authentication: Option[Authentication],
    followHttpToHttpsRedirections: Boolean,
    followHttpsToHttpRedirections: Boolean,
    credentials: Seq[DirectCredentials],
    sslSocketFactoryOpt: Option[SSLSocketFactory],
    hostnameVerifierOpt: Option[HostnameVerifier],
    method: String,
    maxRedirectionsOpt: Option[Int]
  ): URLConnection

  def urlConnectionMaybePartial(
    url0: String,
    authentication: Option[Authentication],
    alreadyDownloaded: Long,
    followHttpToHttpsRedirections: Boolean,
    followHttpsToHttpRedirections: Boolean,
    autoCredentials: Seq[DirectCredentials],
    sslSocketFactoryOpt: Option[SSLSocketFactory],
    hostnameVerifierOpt: Option[HostnameVerifier],
    method: String,
    maxRedirectionsOpt: Option[Int]
  ): (URLConnection, Boolean)

}
