package coursier.core

import java.nio.charset.StandardCharsets
import java.util.Base64

import dataclass.data

@data class Authentication(
  user: String,
  passwordOpt: Option[String],
  httpHeaders: Seq[(String, String)],
  optional: Boolean,
  realmOpt: Option[String],
  httpsOnly: Boolean,
  passOnRedirect: Boolean
) {

  override def toString: String =
    s"Authentication($user, ****, ${httpHeaders.map { case (k, v) => (k, "****") }}, $optional, $realmOpt, $httpsOnly, $passOnRedirect)"


  def withPassword(password: String): Authentication =
    withPasswordOpt(Some(password))
  def withRealm(realm: String): Authentication =
    withRealmOpt(Some(realm))

  def userOnly: Boolean =
    this == Authentication(user)

  def allHttpHeaders: Seq[(String, String)] = {
    val basicAuthHeader = passwordOpt.toSeq.map { p =>
      ("Authorization", "Basic " + Authentication.basicAuthenticationEncode(user, p))
    }
    basicAuthHeader ++ httpHeaders
  }

}

object Authentication {

  def apply(user: String): Authentication =
    Authentication(user, None, Nil, optional = false, None, httpsOnly = true, passOnRedirect = false)
  def apply(user: String, password: String): Authentication =
    Authentication(user, Some(password), Nil, optional = false, None, httpsOnly = true, passOnRedirect = false)

  def apply(
    user: String,
    passwordOpt: Option[String],
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    new Authentication(user, passwordOpt, Nil, optional, realmOpt, httpsOnly, passOnRedirect)

  def apply(
    user: String,
    password: String,
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    Authentication(user, Some(password), Nil, optional, realmOpt, httpsOnly, passOnRedirect)

  def apply(httpHeaders: Seq[(String, String)]): Authentication =
    Authentication("", None, httpHeaders, optional = false, None, httpsOnly = true, passOnRedirect = false)

  def apply(
    httpHeaders: Seq[(String, String)],
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    Authentication("", None, httpHeaders, optional, realmOpt, httpsOnly, passOnRedirect)


  private[coursier] def basicAuthenticationEncode(user: String, password: String): String =
    Base64.getEncoder.encodeToString(
      s"$user:$password".getBytes(StandardCharsets.UTF_8)
    )

}
