package coursier.credentials

import utest._

object DirectCredentialsTests extends TestSuite {

  val tests = Tests {
    "no password in toString" - {
      val cred = DirectCredentials("host", "alex", "1234")
      assert(cred.usernameOpt.contains("alex"))
      assert(cred.passwordOpt.map(_.value).contains("1234"))
      assert(cred.toString.contains("alex"))
      assert(!cred.toString.contains("1234"))
    }
  }

}
