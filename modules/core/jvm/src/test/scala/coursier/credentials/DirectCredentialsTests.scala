package coursier.credentials

import org.scalatest.propspec.AnyPropSpec

class DirectCredentialsTests extends AnyPropSpec {

  property("no password in toString") {
    val cred = DirectCredentials("host", "alex", "1234")
    assert(cred.usernameOpt.contains("alex"))
    assert(cred.passwordOpt.contains("1234"))
    assert(cred.toString.contains("alex"))
    assert(!cred.toString.contains("1234"))
  }

}
