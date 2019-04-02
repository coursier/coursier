/**
 * This code USED TO BE generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO EDIT MANUALLY from now on
package coursier
final class Credentials private (
  val host: String,
  val username: String,
  val password: String,
  val realm: Option[String],
  val optional: Boolean) extends coursier.internal.CredentialsHelpers with Serializable {
  
  private def this() = this("", "", "", None, true)
  private def this(host: String, username: String, password: String) = this(host, username, password, None, true)
  private def this(host: String, username: String, password: String, realm: Option[String]) = this(host, username, password, realm, true)
  
  override def equals(o: Any): Boolean = o match {
    case x: Credentials => (this.host == x.host) && (this.username == x.username) && (this.password == x.password) && (this.realm == x.realm) && (this.optional == x.optional)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "coursier.Credentials".##) + host.##) + username.##) + password.##) + realm.##) + optional.##)
  }
  override def toString: String = {
    "Credentials(" + host + ", " + username + ", " + password + ", " + realm + ", " + optional + ")"
  }
  private[this] def copy(host: String = host, username: String = username, password: String = password, realm: Option[String] = realm, optional: Boolean = optional): Credentials = {
    new Credentials(host, username, password, realm, optional)
  }
  def withHost(host: String): Credentials = {
    copy(host = host)
  }
  def withUsername(username: String): Credentials = {
    copy(username = username)
  }
  def withPassword(password: String): Credentials = {
    copy(password = password)
  }
  def withRealm(realm: Option[String]): Credentials = {
    copy(realm = realm)
  }
  def withRealm(realm: String): Credentials = {
    copy(realm = Option(realm))
  }
  def withOptional(optional: Boolean): Credentials = {
    copy(optional = optional)
  }
}
object Credentials {
  
  def apply(): Credentials = new Credentials()
  def apply(host: String, username: String, password: String): Credentials = new Credentials(host, username, password)
  def apply(host: String, username: String, password: String, realm: Option[String]): Credentials = new Credentials(host, username, password, realm)
  def apply(host: String, username: String, password: String, realm: String): Credentials = new Credentials(host, username, password, Option(realm))
  def apply(host: String, username: String, password: String, realm: Option[String], optional: Boolean): Credentials = new Credentials(host, username, password, realm, optional)
  def apply(host: String, username: String, password: String, realm: String, optional: Boolean): Credentials = new Credentials(host, username, password, Option(realm), optional)
}
