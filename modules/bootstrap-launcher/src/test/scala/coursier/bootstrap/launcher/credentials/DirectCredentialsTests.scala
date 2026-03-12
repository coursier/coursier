package coursier.bootstrap.launcher.credentials

import utest._

import scala.compat.java8.OptionConverters._

object DirectCredentialsTests extends TestSuite {

  val tests = Tests {
    test("no password in toString") {
      val cred = new DirectCredentials("host", "alex", "1234")
      assert(cred.getUsernameOpt.asScala.contains("alex"))
      assert(cred.getPasswordOpt.asScala.map(_.getValue).contains("1234"))
      assert(cred.toString.contains("alex"))
      assert(!cred.toString.contains("1234"))
    }
  }

}
