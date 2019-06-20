package coursier.credentials

import java.io.File

import utest._

object FileCredentialsParseTests extends TestSuite {

  val tests = Tests {

    * - {

      val credFilePath = Option(getClass.getResource("/simple-credentials.properties"))
        .map(u => new File(u.toURI).getAbsolutePath)
        .getOrElse {
          throw new Exception("simple-credentials.properties resource not found")
        }
      val credFile = new File(credFilePath)
      assert(credFile.exists())

      val parsed = FileCredentials(credFilePath).get().sortBy(_.usernameOpt.getOrElse(""))
      val expected = Seq(
        DirectCredentials("127.0.0.1", "secure", "sEcUrE", Some("secure realm"))
          .withOptional(true)
          .withMatchHost(false)
          .withHttpsOnly(true),
        DirectCredentials("127.0.0.1", "simple", "SiMpLe", Some("simple realm"))
          .withOptional(true)
          .withMatchHost(false)
          .withHttpsOnly(false)
      )

      assert(parsed == expected)
    }

  }

}
