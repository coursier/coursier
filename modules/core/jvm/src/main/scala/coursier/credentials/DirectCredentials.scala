package coursier.credentials

import java.net.URI

import coursier.core.Authentication

final class DirectCredentials private(
  val host: String,
  val usernameOpt: Option[String],
  val passwordOpt: Option[String],
  val realm: Option[String],
  val optional: Boolean,
  val matchHost: Boolean,
  val httpsOnly: Boolean,
  val passOnRedirect: Boolean
) extends Credentials {

  @deprecated("Use usernameOpt instead", "2.0.0-RC3")
  def username: String =
    usernameOpt.getOrElse("")
  @deprecated("Use passwordOpt instead", "2.0.0-RC3")
  def password: String =
    passwordOpt.getOrElse("")

  private def this() = this("", None, None, None, true, false, true, false)
  private def this(host: String, username: String, password: String) = this(host, Some(username), Some(password), None, true, false, true, false)
  private def this(host: String, usernameOpt: Option[String], passwordOpt: Option[String]) = this(host, usernameOpt, passwordOpt, None, true, false, true, false)
  private def this(host: String, username: String, password: String, realm: Option[String]) = this(host, Some(username), Some(password), realm, true, false, true, false)
  private def this(host: String, username: String, password: String, realm: Option[String], optional: Boolean) = this(host, Some(username), Some(password), realm, optional, false, true, false)

  // needs to be public for mima it seems
  @deprecated("Public for binary compatibility, not to be called directly", "2.0.0-RC3")
  def this(host: String, username: String, password: String, realm: Option[String], optional: Boolean, matchHost: Boolean, httpsOnly: Boolean, passOnRedirect: Boolean) = this(host, Some(username), Some(password), realm, optional, matchHost, httpsOnly, passOnRedirect)

  override def equals(o: Any): Boolean = o match {
    case x: DirectCredentials => (this.host == x.host) && (this.usernameOpt == x.usernameOpt) && (this.passwordOpt == x.passwordOpt) && (this.realm == x.realm) && (this.optional == x.optional) && (this.matchHost == x.matchHost) && (this.httpsOnly == x.httpsOnly) && (this.passOnRedirect == x.passOnRedirect)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "coursier.credentials.DirectCredentials".##) + host.##) + usernameOpt.##) + passwordOpt.##) + realm.##) + optional.##) + matchHost.##) + httpsOnly.##) + passOnRedirect.##)
  }
  override def toString: String = {
    "Credentials(" + host + ", " + usernameOpt + ", " + "****" + ", " + realm + ", " + optional + ", " + matchHost + ", " + httpsOnly + ", " + passOnRedirect + ")"
  }
  private[this] def copy(host: String = host, usernameOpt: Option[String] = usernameOpt, passwordOpt: Option[String] = passwordOpt, realm: Option[String] = realm, optional: Boolean = optional, matchHost: Boolean = matchHost, httpsOnly: Boolean = httpsOnly, passOnRedirect: Boolean = passOnRedirect): DirectCredentials = {
    new DirectCredentials(host, usernameOpt, passwordOpt, realm, optional, matchHost, httpsOnly, passOnRedirect)
  }
  def withHost(host: String): DirectCredentials = {
    copy(host = host)
  }
  def withUsername(username: String): DirectCredentials =
    copy(usernameOpt = Some(username))
  def withUsername(usernameOpt: Option[String]): DirectCredentials =
    copy(usernameOpt = usernameOpt)
  def withPassword(password: String): DirectCredentials =
    copy(passwordOpt = Some(password))
  def withPassword(passwordOpt: Option[String]): DirectCredentials =
    copy(passwordOpt = passwordOpt)
  def withRealm(realm: Option[String]): DirectCredentials = {
    copy(realm = realm)
  }
  def withRealm(realm: String): DirectCredentials = {
    copy(realm = Option(realm))
  }
  def withOptional(optional: Boolean): DirectCredentials = {
    copy(optional = optional)
  }
  def withMatchHost(matchHost: Boolean): DirectCredentials =
    copy(matchHost = matchHost)
  def withHttpsOnly(httpsOnly: Boolean): DirectCredentials =
    copy(httpsOnly = httpsOnly)
  def withPassOnRedirect(passOnRedirect: Boolean): DirectCredentials =
    copy(passOnRedirect = passOnRedirect)

  private def nonEmpty: Boolean =
    usernameOpt.nonEmpty && passwordOpt.nonEmpty

  // Can be called during redirections, to check whether these credentials apply to the redirection target
  def autoMatches(url: String, realm0: Option[String]): Boolean =
    nonEmpty && matchHost && {
      val uri = new URI(url)
      val schemeOpt = Option(uri.getScheme)
      val hostOpt = Option(uri.getHost)
      ((schemeOpt.contains("http") && !httpsOnly) || schemeOpt.contains("https")) &&
        hostOpt.contains(host) &&
        realm.forall(realm0.contains)
    }

  // Only called on initial artifact URLs, no on the ones originating from redirections
  def matches(url: String, user: String): Boolean =
    nonEmpty && {
      val uri = new URI(url)
      val schemeOpt = Option(uri.getScheme)
      val hostOpt = Option(uri.getHost)
      val userInfoOpt = Option(uri.getUserInfo)
      // !matchHost && // ?
      userInfoOpt.isEmpty &&
        ((schemeOpt.contains("http") && !httpsOnly) || schemeOpt.contains("https")) &&
        hostOpt.contains(host) &&
        usernameOpt.contains(user)
    }

  def authentication: Authentication =
    Authentication(
      username,
      password,
      realmOpt = realm,
      optional = optional,
      httpsOnly = httpsOnly,
      passOnRedirect = passOnRedirect
    )

  def get(): Seq[DirectCredentials] =
    Seq(this)

}
object DirectCredentials {

  def apply(): DirectCredentials = new DirectCredentials()
  def apply(host: String, username: String, password: String): DirectCredentials = new DirectCredentials(host, username, password)
  def apply(host: String, usernameOpt: Option[String], passwordOpt: Option[String]): DirectCredentials = new DirectCredentials(host, usernameOpt, passwordOpt)
  def apply(host: String, username: String, password: String, realm: Option[String]): DirectCredentials = new DirectCredentials(host, username, password, realm)
  def apply(host: String, username: String, password: String, realm: String): DirectCredentials = new DirectCredentials(host, username, password, Option(realm))
  def apply(host: String, username: String, password: String, realm: Option[String], optional: Boolean): DirectCredentials = new DirectCredentials(host, username, password, realm, optional)
  def apply(host: String, username: String, password: String, realm: String, optional: Boolean): DirectCredentials = new DirectCredentials(host, username, password, Option(realm), optional)
  def apply(host: String, username: String, password: String, realm: Option[String], optional: Boolean, passOnRedirect: Boolean): DirectCredentials = new DirectCredentials(host, Some(username), Some(password), realm, optional, false, false, passOnRedirect)
  def apply(host: String, username: String, password: String, realm: String, optional: Boolean, passOnRedirect: Boolean): DirectCredentials = new DirectCredentials(host, Some(username), Some(password), Option(realm), optional, false, false, passOnRedirect)
}
