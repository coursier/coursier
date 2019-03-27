/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package coursier.cache
final class Credentials private (
  val realm: Option[String],
  val host: String,
  val username: String,
  val password: String) extends coursier.cache.internal.CredentialsHelpers with Serializable {
  
  private def this() = this(None, "", "", "")
  
  override def equals(o: Any): Boolean = o match {
    case x: Credentials => (this.realm == x.realm) && (this.host == x.host) && (this.username == x.username) && (this.password == x.password)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "coursier.cache.Credentials".##) + realm.##) + host.##) + username.##) + password.##)
  }
  override def toString: String = {
    "Credentials(" + realm + ", " + host + ", " + username + ", " + password + ")"
  }
  private[this] def copy(realm: Option[String] = realm, host: String = host, username: String = username, password: String = password): Credentials = {
    new Credentials(realm, host, username, password)
  }
  def withRealm(realm: Option[String]): Credentials = {
    copy(realm = realm)
  }
  def withRealm(realm: String): Credentials = {
    copy(realm = Option(realm))
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
}
object Credentials {
  
  def apply(): Credentials = new Credentials()
  def apply(realm: Option[String], host: String, username: String, password: String): Credentials = new Credentials(realm, host, username, password)
  def apply(realm: String, host: String, username: String, password: String): Credentials = new Credentials(Option(realm), host, username, password)
}
