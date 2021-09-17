package coursier.bootstrap.launcher.credentials

import utest._

import scala.jdk.CollectionConverters._
import scala.compat.java8.OptionConverters._
import java.io.File

object FileCredentialsParseTests extends TestSuite {

  val tests = Tests {

    test {

      val credFilePath = Option(getClass.getResource("/bootstrap-credentials.properties"))
        .map(u => new File(u.toURI).getAbsolutePath)
        .getOrElse {
          throw new Exception("bootstrap-credentials.properties resource not found")
        }
      val credFile = new File(credFilePath)
      assert(credFile.exists())

      val parsed = new FileCredentials(credFilePath)
        .get()
        .asScala
        .sortBy(_.getUsernameOpt.asScala.getOrElse(""))
      val expected = Seq(
        new DirectCredentials("127.0.0.1", "secure", "sEcUrE", "secure realm")
          .withOptional(true)
          .withHttpsOnly(true),
        new DirectCredentials("127.0.0.1", "simple", "SiMpLe", "simple realm")
          .withOptional(true)
          .withHttpsOnly(false)
      )

      assert(parsed == expected)
    }

  }

}
