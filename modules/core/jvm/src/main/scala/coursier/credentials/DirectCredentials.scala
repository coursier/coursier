package coursier.credentials

import java.net.URI

import coursier.core.Authentication

final class DirectCredentials private(
  val host: String,
  val username: String,
  val password: String,
  val realm: Option[String],
  val optional: Boolean
) extends Serializable {

  private def this() = this("", "", "", None, true)
  private def this(host: String, username: String, password: String) = this(host, username, password, None, true)
  private def this(host: String, username: String, password: String, realm: Option[String]) = this(host, username, password, realm, true)

  override def equals(o: Any): Boolean = o match {
    case x: DirectCredentials => (this.host == x.host) && (this.username == x.username) && (this.password == x.password) && (this.realm == x.realm) && (this.optional == x.optional)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "coursier.credentials.DirectCredentials".##) + host.##) + username.##) + password.##) + realm.##) + optional.##)
  }
  override def toString: String = {
    "Credentials(" + host + ", " + username + ", " + password + ", " + realm + ", " + optional + ")"
  }
  private[this] def copy(host: String = host, username: String = username, password: String = password, realm: Option[String] = realm, optional: Boolean = optional): DirectCredentials = {
    new DirectCredentials(host, username, password, realm, optional)
  }
  def withHost(host: String): DirectCredentials = {
    copy(host = host)
  }
  def withUsername(username: String): DirectCredentials = {
    copy(username = username)
  }
  def withPassword(password: String): DirectCredentials = {
    copy(password = password)
  }
  def withRealm(realm: Option[String]): DirectCredentials = {
    copy(realm = realm)
  }
  def withRealm(realm: String): DirectCredentials = {
    copy(realm = Option(realm))
  }
  def withOptional(optional: Boolean): DirectCredentials = {
    copy(optional = optional)
  }

  def matches(url: String, realm0: Option[String]): Boolean = {
    val uri = new URI(url)
    val scheme = uri.getScheme
    val host0 = uri.getHost
    (scheme == "http" || scheme == "https") && host == host0 && realm.forall(realm0.contains)
  }

  def authentication: Authentication =
    Authentication(
      username,
      password,
      realmOpt = realm,
      optional = optional
    )

}
object DirectCredentials {

  def apply(): DirectCredentials = new DirectCredentials()
  def apply(host: String, username: String, password: String): DirectCredentials = new DirectCredentials(host, username, password)
  def apply(host: String, username: String, password: String, realm: Option[String]): DirectCredentials = new DirectCredentials(host, username, password, realm)
  def apply(host: String, username: String, password: String, realm: String): DirectCredentials = new DirectCredentials(host, username, password, Option(realm))
  def apply(host: String, username: String, password: String, realm: Option[String], optional: Boolean): DirectCredentials = new DirectCredentials(host, username, password, realm, optional)
  def apply(host: String, username: String, password: String, realm: String, optional: Boolean): DirectCredentials = new DirectCredentials(host, username, password, Option(realm), optional)
}
