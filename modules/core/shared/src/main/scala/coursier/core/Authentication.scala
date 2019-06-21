package coursier.core

import java.nio.charset.StandardCharsets
import java.util.Base64

final class Authentication private (
  val user: String,
  val passwordOpt: Option[String],
  val httpHeaders: Seq[(String, String)],
  val optional: Boolean,
  val realmOpt: Option[String],
  val httpsOnly: Boolean,
  val passOnRedirect: Boolean
) {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Authentication =>
        user == other.user &&
          passwordOpt == other.passwordOpt &&
          httpHeaders == other.httpHeaders &&
          optional == other.optional &&
          realmOpt == other.realmOpt &&
          httpsOnly == other.httpsOnly &&
          passOnRedirect == other.passOnRedirect
      case _ => false
    }

  override def hashCode(): Int = {
    var code = 17 + "coursier.core.Authentication".##
    code = 37 * code + user.##
    code = 37 * code + passwordOpt.##
    code = 37 * code + httpHeaders.##
    code = 37 * code + optional.##
    code = 37 * code + realmOpt.##
    code = 37 * code + httpsOnly.##
    code = 37 * code + passOnRedirect.##
    code
  }

  override def toString: String =
    s"Authentication($user, ****, ${httpHeaders.map { case (k, v) => (k, "****") }}, $optional, $realmOpt, $httpsOnly, $passOnRedirect)"


  private def copy(
    user: String = user,
    passwordOpt: Option[String] = passwordOpt,
    httpHeaders: Seq[(String, String)] = httpHeaders,
    optional: Boolean = optional,
    realmOpt: Option[String] = realmOpt,
    httpsOnly: Boolean = httpsOnly,
    passOnRedirect: Boolean = passOnRedirect
  ): Authentication =
    new Authentication(user, passwordOpt, httpHeaders, optional, realmOpt, httpsOnly, passOnRedirect)

  def withUser(user: String): Authentication =
    copy(user = user)
  def withPassword(password: String): Authentication =
    copy(passwordOpt = Some(password))
  def withPassword(passwordOpt: Option[String]): Authentication =
    copy(passwordOpt = passwordOpt)
  def withHttpHeaders(httpHeaders: Seq[(String, String)]): Authentication =
    copy(httpHeaders = httpHeaders)
  def withOptional(optional: Boolean): Authentication =
    copy(optional = optional)
  def withRealm(realm: String): Authentication =
    copy(realmOpt = Some(realm))
  def withRealm(realmOpt: Option[String]): Authentication =
    copy(realmOpt = realmOpt)
  def withHttpsOnly(httpsOnly: Boolean): Authentication =
    copy(httpsOnly = httpsOnly)
  def withPassOnRedirect(passOnRedirect: Boolean): Authentication =
    copy(passOnRedirect = passOnRedirect)

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
    new Authentication(user, None, Nil, optional = false, None, httpsOnly = true, passOnRedirect = false)
  def apply(user: String, password: String): Authentication =
    new Authentication(user, Some(password), Nil, optional = false, None, httpsOnly = true, passOnRedirect = false)

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
    new Authentication(user, Some(password), Nil, optional, realmOpt, httpsOnly, passOnRedirect)

  def apply(httpHeaders: Seq[(String, String)]): Authentication =
    new Authentication("", None, httpHeaders, optional = false, None, httpsOnly = true, passOnRedirect = false)

  def apply(
    httpHeaders: Seq[(String, String)],
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    new Authentication("", None, httpHeaders, optional, realmOpt, httpsOnly, passOnRedirect)


  private[coursier] def basicAuthenticationEncode(user: String, password: String): String =
    Base64.getEncoder.encodeToString(
      s"$user:$password".getBytes(StandardCharsets.UTF_8)
    )

}
