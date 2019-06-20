package coursier.core

final class Authentication private (
  val user: String,
  val password: String,
  val optional: Boolean,
  val realmOpt: Option[String],
  val httpsOnly: Boolean,
  val passOnRedirect: Boolean
) {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Authentication =>
        user == other.user &&
          password == other.password &&
          optional == other.optional &&
          realmOpt == other.realmOpt &&
          httpsOnly == other.httpsOnly &&
          passOnRedirect == other.passOnRedirect
      case _ => false
    }

  override def hashCode(): Int = {
    var code = 17 + "coursier.core.Authentication".##
    code = 37 * code + user.##
    code = 37 * code + password.##
    code = 37 * code + optional.##
    code = 37 * code + realmOpt.##
    code = 37 * code + httpsOnly.##
    code = 37 * code + passOnRedirect.##
    code
  }

  override def toString: String =
    s"Authentication($user, ****, $optional, $realmOpt, $httpsOnly, $passOnRedirect)"


  private def copy(
    user: String = user,
    password: String = password,
    optional: Boolean = optional,
    realmOpt: Option[String] = realmOpt,
    httpsOnly: Boolean = httpsOnly,
    passOnRedirect: Boolean = passOnRedirect
  ): Authentication =
    new Authentication(user, password, optional, realmOpt, httpsOnly, passOnRedirect)

  def withUser(user: String): Authentication =
    copy(user = user)
  def withPassword(password: String): Authentication =
    copy(password = password)
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

}

object Authentication {

  def apply(user: String): Authentication =
    new Authentication(user, "", optional = false, None, httpsOnly = true, passOnRedirect = false)
  def apply(user: String, password: String): Authentication =
    new Authentication(user, password, optional = false, None, httpsOnly = true, passOnRedirect = false)

  def apply(
    user: String,
    password: String,
    optional: Boolean,
    realmOpt: Option[String],
    httpsOnly: Boolean,
    passOnRedirect: Boolean
  ): Authentication =
    new Authentication(user, password, optional, realmOpt, httpsOnly, passOnRedirect)

}
