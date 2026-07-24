package coursier.core

import dataclass.{data, since => unroll}

import java.nio.charset.StandardCharsets
import java.util.Base64

@data case class Authentication(
  userOpt: Option[String],
  passwordOpt: Option[String],
  httpHeaders: Seq[(String, String)],
  optional: Boolean,
  realmOpt: Option[String],
  httpsOnly: Boolean,
  passOnRedirect: Boolean,
  @unroll
  byNameHttpHeaders: Seq[() => Seq[(String, String)]] = Nil
) {

  @deprecated("Use userOpt instead", "2.1.25")
  def user: String =
    userOpt.getOrElse {
      sys.error("Deprecated method not supported when userOpt is empty")
    }

  def withUser(newUser: String): Authentication =
    copy(userOpt = Some(newUser))

  override def toString: String = {
    val headersStr = httpHeaders.map {
      case (k, v) =>
        (k, "****")
    }
    val byNameHeadersStr = byNameHttpHeaders.map { _ =>
      "[lazy]"
    }
    s"Authentication($userOpt, ****, $headersStr, $byNameHeadersStr, $optional, $realmOpt, $httpsOnly, $passOnRedirect)"
  }

  def withPassword(password: String): Authentication =
    copy(passwordOpt = Some(password))
  def withRealm(realm: String): Authentication =
    copy(realmOpt = Some(realm))

  def userOnly: Boolean =
    userOpt.forall { user =>
      this == Authentication.create(user)
    }

  def allHttpHeaders: Seq[(String, String)] = {
    val basicAuthHeader =
      for {
        user     <- userOpt
        password <- passwordOpt
      } yield ("Authorization", "Basic " + Authentication.basicAuthenticationEncode(user, password))
    basicAuthHeader.toSeq ++ httpHeaders ++ byNameHttpHeaders.flatMap(_())
  }

}

object Authentication {

  def create(user: String): Authentication =
    Authentication(
      Some(user),
      None,
      Nil,
      optional = false,
      None,
      httpsOnly = true,
      passOnRedirect = false
    )
  def create(user: String, password: String): Authentication =
    Authentication(
      Some(user),
      Some(password),
      Nil,
      optional = false,
      None,
      httpsOnly = true,
      passOnRedirect = false
    )

  def create(
    user: String,
    passwordOpt: Option[String],
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    new Authentication(Some(user), passwordOpt, Nil, optional, realmOpt, httpsOnly, passOnRedirect)

  def create(
    user: String,
    password: String,
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    Authentication(Some(user), Some(password), Nil, optional, realmOpt, httpsOnly, passOnRedirect)

  def create(httpHeaders: Seq[(String, String)]): Authentication =
    Authentication(
      Some(""),
      None,
      httpHeaders,
      optional = false,
      None,
      httpsOnly = true,
      passOnRedirect = false
    )

  def create(
    httpHeaders: Seq[(String, String)],
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    Authentication(Some(""), None, httpHeaders, optional, realmOpt, httpsOnly, passOnRedirect)

  private[coursier] def basicAuthenticationEncode(user: String, password: String): String =
    Base64.getEncoder.encodeToString(
      s"$user:$password".getBytes(StandardCharsets.UTF_8)
    )

  def bearerToken(token: String): Authentication =
    Authentication(
      None,
      None,
      Seq(
        "Authorization" -> s"Bearer $token"
      ),
      optional = false,
      None,
      httpsOnly = true,
      passOnRedirect = false
    )

  def byNameBearerToken(token: => String): Authentication =
    Authentication(
      None,
      None,
      Nil,
      optional = false,
      None,
      httpsOnly = true,
      passOnRedirect = false,
      byNameHttpHeaders = Seq(() =>
        Seq(
          "Authorization" -> s"Bearer $token"
        )
      )
    )

}
