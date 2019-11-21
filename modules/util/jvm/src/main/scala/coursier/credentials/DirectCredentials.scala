package coursier.credentials

import java.net.URI

import coursier.core.Authentication

import scala.util.Try
import dataclass._

@data class DirectCredentials(
  host: String = "",
  usernameOpt: Option[String] = None,
  passwordOpt: Option[Password[String]] = None,
  @since
  realm: Option[String] = None,
  @since
  optional: Boolean = true,
  @since
  matchHost: Boolean = false,
  httpsOnly: Boolean = true,
  passOnRedirect: Boolean = false
) extends Credentials {

  def withUsername(username: String): DirectCredentials =
    withUsernameOpt(Some(username))
  def withPassword(password: String): DirectCredentials =
    withPasswordOpt(Some(Password(password)))
  def withRealm(realm: String): DirectCredentials =
    withRealm(Option(realm))

  private def nonEmpty: Boolean =
    usernameOpt.nonEmpty && passwordOpt.nonEmpty

  // Can be called during redirections, to check whether these credentials apply to the redirection target
  def autoMatches(url: String, realm0: Option[String]): Boolean =
    nonEmpty && matchHost && {
      val uriOpt = Try(new URI(url)).toOption
      val schemeOpt = uriOpt.flatMap(uri => Option(uri.getScheme))
      val hostOpt = uriOpt.flatMap(uri => Option(uri.getHost))
      ((schemeOpt.contains("http") && !httpsOnly) || schemeOpt.contains("https")) &&
        hostOpt.contains(host) &&
        realm.forall(realm0.contains)
    }

  // Only called on initial artifact URLs, not on the ones originating from redirections
  def matches(url: String, user: String): Boolean =
    nonEmpty && {
      val uriOpt = Try(new URI(url)).toOption
      val schemeOpt = uriOpt.flatMap(uri => Option(uri.getScheme))
      val hostOpt = uriOpt.flatMap(uri => Option(uri.getHost))
      val userInfoOpt = uriOpt.flatMap(uri => Option(uri.getUserInfo))
      // !matchHost && // ?
      userInfoOpt.isEmpty &&
        ((schemeOpt.contains("http") && !httpsOnly) || schemeOpt.contains("https")) &&
        hostOpt.contains(host) &&
        usernameOpt.contains(user)
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

  def apply(host: String, username: String, password: String): DirectCredentials = DirectCredentials(host, Some(username), Some(Password(password)))
  def apply(host: String, username: String, password: String, realm: Option[String]): DirectCredentials = DirectCredentials(host, Some(username), Some(Password(password)), realm)
}
