package coursier.proxy

import java.net.Authenticator
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import utest._

object SetupProxyTests extends TestSuite {

  private val tunnelingKey = "jdk.http.auth.tunneling.disabledSchemes"
  private val proxyingKey  = "jdk.http.auth.proxying.disabledSchemes"

  private val proxyKeys =
    Seq("http", "https").flatMap { scheme =>
      Seq("proxyProtocol", "proxyHost", "proxyPort", "proxyUser", "proxyPassword")
        .map(name => s"$scheme.$name")
    } :+ "http.nonProxyHosts"

  // SetupProxy changes system properties, restore them after each test
  private def withProperties[T](values: (String, String)*)(f: => T): T = {
    val keys  = (proxyKeys ++ Seq(tunnelingKey, proxyingKey)).distinct
    val saved = keys.map(k => k -> Option(System.getProperty(k)))
    try {
      keys.foreach(System.clearProperty)
      for ((k, v) <- values)
        System.setProperty(k, v)
      f
    }
    finally {
      Authenticator.setDefault(null)
      for ((k, vOpt) <- saved)
        vOpt match {
          case Some(v) => System.setProperty(k, v)
          case None    => System.clearProperty(k)
        }
    }
  }

  private def tunnelingProp(): Option[String] =
    Option(System.getProperty(tunnelingKey))

  val tests = Tests {

    test("removeScheme") {
      assert(SetupProxy.removeScheme("Basic", "Basic") == "")
      assert(SetupProxy.removeScheme("", "Basic") == "")
      assert(SetupProxy.removeScheme("basic, Digest", "Basic") == "Digest")
      assert(SetupProxy.removeScheme("NTLM,Basic,Digest", "Basic") == "NTLM,Digest")
      assert(SetupProxy.removeScheme("Digest", "Basic") == "Digest")
    }

    test("no credentials") {
      test("address") {
        withProperties() {
          SetupProxy.setProxyProperties("http://localhost:3128", null, null, null)
          assert(System.getProperty("https.proxyHost") == "localhost")
          assert(!SetupProxy.setupAuthenticator())
          assert(tunnelingProp().isEmpty)
          assert(System.getProperty(proxyingKey) == null)
        }
      }
      test("settings.xml") {
        withProperties() {
          withSettings(
            """<settings>
              |  <proxies>
              |    <proxy>
              |      <protocol>http</protocol>
              |      <host>localhost</host>
              |      <port>3128</port>
              |    </proxy>
              |  </proxies>
              |</settings>
              |""".stripMargin
          )
          assert(System.getProperty("https.proxyHost") == "localhost")
          assert(!SetupProxy.setupAuthenticator())
          assert(tunnelingProp().isEmpty)
        }
      }
    }

    test("credentials") {
      test("address") {
        withProperties() {
          SetupProxy.setProxyProperties("http://localhost:3128", "user", "pass", null)
          assert(SetupProxy.setupAuthenticator())
          val value = tunnelingProp()
          assert(value.nonEmpty)
          assert(!value.get.split(",").map(_.trim).exists(_.equalsIgnoreCase("Basic")))
          assert(System.getProperty(proxyingKey) == null)
        }
      }
      test("settings.xml") {
        withProperties() {
          withSettings(
            """<settings>
              |  <proxies>
              |    <proxy>
              |      <protocol>http</protocol>
              |      <host>localhost</host>
              |      <port>3128</port>
              |      <username>user</username>
              |      <password>pass</password>
              |    </proxy>
              |  </proxies>
              |</settings>
              |""".stripMargin
          )
          assert(SetupProxy.setupAuthenticator())
          val value = tunnelingProp()
          assert(value.nonEmpty)
          assert(!value.get.split(",").map(_.trim).exists(_.equalsIgnoreCase("Basic")))
        }
      }
      test("java properties") {
        withProperties(
          "https.proxyHost"     -> "localhost",
          "https.proxyPort"     -> "3128",
          "https.proxyUser"     -> "user",
          "https.proxyPassword" -> "pass"
        ) {
          assert(SetupProxy.setupAuthenticator())
          val value = tunnelingProp()
          assert(value.nonEmpty)
          assert(!value.get.split(",").map(_.trim).exists(_.equalsIgnoreCase("Basic")))
        }
      }
      test("keep user value") {
        withProperties(tunnelingKey -> "Basic,Digest") {
          SetupProxy.setProxyProperties("http://localhost:3128", "user", "pass", null)
          assert(SetupProxy.setupAuthenticator())
          assert(tunnelingProp() == Some("Basic,Digest"))
        }
      }
    }
  }

  private def withSettings(content: String): Unit = {
    val f = Files.createTempFile("coursier-settings", ".xml")
    try {
      Files.write(f, content.getBytes(StandardCharsets.UTF_8))
      SetupProxy.setupPropertiesFrom(f.toFile, "")
    }
    finally Files.delete(f)
  }
}
