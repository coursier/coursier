package coursier.credentials

import dataclass.{data, since => unroll}

import java.net.URI

import coursier.core.Authentication

import scala.util.Try

@data case class DirectCredentials(
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
      val uriOpt    = Try(new URI(url)).toOption
      val schemeOpt = uriOpt.flatMap(uri => Option(uri.getScheme))
      val hostOpt   = uriOpt.flatMap(uri => Option(uri.getHost))
      ((schemeOpt.contains("http") && !httpsOnly) || schemeOpt.contains("https")) &&
      hostOpt.contains(host) &&
      realm.forall(realm0.contains)
    }

  // Only called on initial artifact URLs, not on the ones originating from redirections
  def matches(url: String, user: String): Boolean =
    nonEmpty && {
      val uriOpt      = Try(new URI(url)).toOption
      val schemeOpt   = uriOpt.flatMap(uri => Option(uri.getScheme))
      val hostOpt     = uriOpt.flatMap(uri => Option(uri.getHost))
      val userInfoOpt = uriOpt.flatMap(uri => Option(uri.getUserInfo))
      // !matchHost && // ?
      userInfoOpt.isEmpty &&
      ((schemeOpt.contains("http") && !httpsOnly) || schemeOpt.contains("https")) &&
      hostOpt.contains(host) &&
      usernameOpt.contains(user)
    }

  def authentication: Authentication =
    Authentication.create(
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

  def create(host: String, username: String, password: String): DirectCredentials =
    DirectCredentials(host, Some(username), Some(Password(password)))
  def create(
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
    false
  def defaultPassOnRedirect: Boolean =
    false
}
