package coursier.core

import java.nio.charset.StandardCharsets
import java.util.Base64

import dataclass.data

@data class Authentication(
  userOpt: Option[String],
  passwordOpt: Option[String],
  httpHeaders: Seq[(String, String)],
  optional: Boolean,
  realmOpt: Option[String],
  httpsOnly: Boolean,
  passOnRedirect: Boolean
) {

  @deprecated("Use the override accepting an Option[String] as user", "2.1.25")
  def this(
    user: String,
    passwordOpt: Option[String],
    httpHeaders: Seq[(String, String)],
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ) =
    this(
      Some(user),
      passwordOpt,
      httpHeaders,
      optional,
      realmOpt,
      httpsOnly,
      passOnRedirect
    )

  @deprecated("Use userOpt instead", "2.1.25")
  def user: String =
    userOpt.getOrElse {
      sys.error("Deprecated method not supported when userOpt is empty")
    }

  def withUser(newUser: String): Authentication =
    withUserOpt(Some(newUser))

  override def toString: String = {
    val headersStr = httpHeaders.map {
      case (k, v) =>
        (k, "****")
    }
    s"Authentication($userOpt, ****, $headersStr, $optional, $realmOpt, $httpsOnly, $passOnRedirect)"
  }

  def withPassword(password: String): Authentication =
    withPasswordOpt(Some(password))
  def withRealm(realm: String): Authentication =
    withRealmOpt(Some(realm))

  def userOnly: Boolean =
    userOpt.forall { user =>
      this == Authentication(user)
    }

  def allHttpHeaders: Seq[(String, String)] = {
    val basicAuthHeader =
      for {
        user     <- userOpt
        password <- passwordOpt
      } yield ("Authorization", "Basic " + Authentication.basicAuthenticationEncode(user, password))
    basicAuthHeader.toSeq ++ httpHeaders
  }

}

object Authentication {

  def apply(user: String): Authentication =
    Authentication(
      Some(user),
      None,
      Nil,
      optional = false,
      None,
      httpsOnly = true,
      passOnRedirect = false
    )
  def apply(user: String, password: String): Authentication =
    Authentication(
      Some(user),
      Some(password),
      Nil,
      optional = false,
      None,
      httpsOnly = true,
      passOnRedirect = false
    )

  def apply(
    user: String,
    passwordOpt: Option[String],
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    new Authentication(Some(user), passwordOpt, Nil, optional, realmOpt, httpsOnly, passOnRedirect)

  def apply(
    user: String,
    password: String,
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    Authentication(Some(user), Some(password), Nil, optional, realmOpt, httpsOnly, passOnRedirect)

  def apply(httpHeaders: Seq[(String, String)]): Authentication =
    Authentication(
      Some(""),
      None,
      httpHeaders,
      optional = false,
      None,
      httpsOnly = true,
      passOnRedirect = false
    )

  def apply(
    httpHeaders: Seq[(String, String)],
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    Authentication(Some(""), None, httpHeaders, optional, realmOpt, httpsOnly, passOnRedirect)

  @deprecated("Use the override accepting an Option[String] as user", "2.1.25")
  def apply(
    user: String,
    passwordOpt: Option[String],
    httpHeaders: Seq[(String, String)],
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    Authentication(
      Some(user),
      passwordOpt,
      httpHeaders,
      optional,
      realmOpt,
      httpsOnly,
      passOnRedirect
    )

  private[coursier] def basicAuthenticationEncode(user: String, password: String): String =
    Base64.getEncoder.encodeToString(
      s"$user:$password".getBytes(StandardCharsets.UTF_8)
    )

}
