package coursier.credentials

import java.net.URI
import java.util.Locale

import coursier.core.Authentication

import scala.util.Try
import dataclass.{data, since => unroll}

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class DirectCredentials(
  host: String = "",
  usernameOpt: Option[String] = None,
  passwordOpt: Option[Password[String]] = None,
  @unroll
  realm: Option[String] = None,
  @unroll
  optional: Boolean = DirectCredentials.defaultOptional,
  @unroll
  matchHost: Boolean = DirectCredentials.defaultMatchHost,
  httpsOnly: Boolean = DirectCredentials.defaultHttpsOnly,
  passOnRedirect: Boolean = DirectCredentials.defaultPassOnRedirect
) extends Credentials {

  def withUsername(username: String): DirectCredentials =
    copy(usernameOpt = Some(username))
  def withPassword(password: String): DirectCredentials =
    copy(passwordOpt = Some(Password(password)))
  def withRealm(realm: String): DirectCredentials =
    copy(realm = Option(realm))

  private def nonEmpty: Boolean =
    usernameOpt.nonEmpty && passwordOpt.nonEmpty

  // Can be called during redirections, to check whether these credentials apply to the redirection target
  def autoMatches(url: String, realm0: Option[String]): Boolean =
    nonEmpty && matchHost && {
      val uriOpt = Try(new URI(url)).toOption
      uriOpt.exists(urlMatches) &&
      realm.forall(realm0.contains)
    }

  // Only called on initial artifact URLs, not on the ones originating from redirections
  def matches(url: String, user: String): Boolean =
    nonEmpty && {
      val uriOpt      = Try(new URI(url)).toOption
      val userInfoOpt = uriOpt.flatMap(uri => Option(uri.getUserInfo))
      // !matchHost && // ?
      userInfoOpt.isEmpty &&
      uriOpt.exists(urlMatches) &&
      usernameOpt.contains(user)
    }

  /** Whether these credentials can be sent to `uri`, judging from its scheme, host, and port
    *
    * `host` is either a bare host name, that matches HTTPS URLs on any port (and HTTP ones too if
    * `httpsOnly` is false), or an origin like `https://repo.example.com:8443`, that only matches
    * that scheme, host, and port (the scheme's default port if none is specified). A bare host can
    * also be followed by a port, like `repo.example.com:8443`, to only match that port.
    */
  private def urlMatches(uri: URI): Boolean = {
    val (hostSchemeOpt, hostName, hostPortOpt) = DirectCredentials.parseHost(host)
    val schemeOpt = Option(uri.getScheme).map(_.toLowerCase(Locale.ROOT))
    val schemeMatches = hostSchemeOpt match {
      case Some(scheme) => schemeOpt.contains(scheme)
      case None         => schemeOpt.contains("https") || (schemeOpt.contains("http") && !httpsOnly)
    }
    val portOpt = Some(uri.getPort)
      .filter(_ >= 0)
      .orElse(schemeOpt.flatMap(DirectCredentials.defaultPort))
    val expectedPortOpt = hostPortOpt.orElse(hostSchemeOpt.flatMap(DirectCredentials.defaultPort))
    schemeMatches &&
    Option(uri.getHost).contains(hostName) &&
    expectedPortOpt.forall(portOpt.contains)
  }

  def authentication: Authentication =
    Authentication(
      usernameOpt.getOrElse(""),
      passwordOpt.map(_.value),
      realmOpt = realm,
      optional = optional,
      httpsOnly = httpsOnly,
      passOnRedirect = passOnRedirect
    )

  def get(): Seq[DirectCredentials] =
    Seq(this)

}
object DirectCredentials {

  def apply(host: String, username: String, password: String): DirectCredentials =
    DirectCredentials(host, Some(username), Some(Password(password)))
  def apply(
    host: String,
    username: String,
    password: String,
    realm: Option[String]
  ): DirectCredentials =
    DirectCredentials(host, Some(username), Some(Password(password)), realm)

  def defaultOptional: Boolean =
    true
  def defaultMatchHost: Boolean =
    true
  def defaultHttpsOnly: Boolean =
    true
  def defaultPassOnRedirect: Boolean =
    false

  private val hostPattern =
    """(?:([a-zA-Z][a-zA-Z0-9+.-]*)://)?(\[[^\]/]*\]|[^:/\[\]]*)(?::([0-9]+))?/?""".r.pattern

  /** Splits a `host` value into its optional scheme (lower-cased), host name, and optional port
    *
    * Values that don't look like `[scheme://]host[:port]` are returned as is, as a host name.
    */
  private[credentials] def parseHost(host: String): (Option[String], String, Option[Int]) = {
    val m = hostPattern.matcher(host)
    if (m.matches())
      (
        Option(m.group(1)).map(_.toLowerCase(Locale.ROOT)),
        m.group(2),
        Option(m.group(3)).flatMap(p => Try(p.toInt).toOption)
      )
    else
      (None, host, None)
  }

  private def defaultPort(scheme: String): Option[Int] =
    scheme match {
      case "https" => Some(443)
      case "http"  => Some(80)
      case _       => None
    }
}
