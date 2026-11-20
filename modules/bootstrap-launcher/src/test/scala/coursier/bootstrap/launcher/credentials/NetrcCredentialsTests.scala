package coursier.bootstrap.launcher.credentials

import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import utest._

import scala.compat.java8.OptionConverters._
import scala.jdk.CollectionConverters._

object NetrcCredentialsTests extends TestSuite {

  private def entries(content: String): Seq[(String, String, String)] =
    NetrcCredentials.parse(content, "test").asScala.toSeq.map { c =>
      (
        if (c.isAnyHost) "*" else c.getHost,
        c.getUsernameOpt.asScala.getOrElse(""),
        c.getPasswordOpt.asScala.fold("")(_.getValue)
      )
    }

  /** The credentials sent to `url`, picked the way `Download` picks them */
  private def lookup(
    content: String,
    url: String,
    user: Option[String] = None
  ): Option[(String, String)] =
    NetrcCredentials.parse(content, "test").asScala
      .find { c =>
        c.isMatchHost &&
        c.getUsernameOpt.isPresent && user.forall(c.getUsernameOpt.get == _) &&
        c.getPasswordOpt.isPresent &&
        c.urlMatches(new URL(url))
      }
      .map(c => (c.getUsernameOpt.get, c.getPasswordOpt.get.getValue))

  private def withTmpFile[T](content: String)(f: Path => T): T = {
    val file = Files.createTempFile("coursier-netrc-tests", ".txt")
    try {
      Files.write(file, content.getBytes(StandardCharsets.UTF_8))
      f(file)
    }
    finally Files.deleteIfExists(file)
  }

  val tests = Tests {

    test("parse") {
      val res = entries(
        """# comment
          |machine repo.example.com login alex password s3cret
          |MACHINE other.example.com
          |  login "bob b" password "b\"0b"
          |macdef init
          |machine ignored.example.com login a password b
          |
          |machine incomplete.example.com login carol
          |default login anon account acc password an0n
          |""".stripMargin
      )
      val expected = Seq(
        ("repo.example.com", "alex", "s3cret"),
        ("other.example.com", "bob b", "b\"0b"),
        ("*", "anon", "an0n")
      )
      assert(res == expected)
    }

    test("syntax error") {
      val content = "machine a login b password c\nmachine d login e password \"f\n"
      assertThrows[RuntimeException](NetrcCredentials.parse(content, "test"))
      val partial = new java.util.ArrayList[DirectCredentials]
      val error   = NetrcCredentials.parseEntries(content, partial)
      assert(error != null)
      assert(partial.asScala.map(_.getHost) == Seq("a"))
    }

    test("matching") {
      val content =
        """machine repo.example.com login alex password s3cret
          |machine repo.example.com login bob password b0b
          |default login anon password an0n
          |""".stripMargin

      test("any scheme, any port, any case") {
        for (
          url <- Seq(
            "https://repo.example.com/a.pom",
            "http://repo.example.com:8081/a.pom",
            "https://REPO.example.com:8443/a.pom"
          )
        )
          assert(lookup(content, url) == Some(("alex", "s3cret")))
      }
      test("user in the URL") {
        val url = "https://repo.example.com/a.pom"
        assert(lookup(content, url, Some("bob")) == Some(("bob", "b0b")))
        assert(lookup(content, url, Some("anon")) == Some(("anon", "an0n")))
        assert(lookup(content, url, Some("dave")).isEmpty)
      }
      test("default entry") {
        assert(lookup(content, "http://other.example.com/a.pom") == Some(("anon", "an0n")))
      }
    }

    test("file") {
      test("optional, missing") {
        assert(new NetrcCredentials("/nonexistent/netrc", true).get().isEmpty)
      }
      test("required, missing") {
        assertThrows[RuntimeException](new NetrcCredentials("/nonexistent/netrc", false).get())
      }
      test("read") {
        withTmpFile("machine a login b password c\n") { file =>
          assert(new NetrcCredentials(
            file.toString,
            false
          ).get().asScala.map(_.getHost) == Seq("a"))
        }
      }
      test("syntax error") {
        withTmpFile("machine a login b password c\nmachine d login \"e\n") { file =>
          assert(new NetrcCredentials(file.toString, true).get().asScala.map(_.getHost) == Seq("a"))
          assertThrows[RuntimeException](new NetrcCredentials(file.toString, false).get())
        }
      }
    }

    test("default file") {
      def candidates(env: Map[String, String], userHome: String, isWindows: Boolean) =
        NetrcCredentials.defaultCandidates(
          (name: String) => env.getOrElse(name, null),
          userHome,
          isWindows
        ).asScala.toSeq
      val home = Paths.get("/home/alex")

      test("COURSIER_NETRC, then NETRC") {
        val env = Map("COURSIER_NETRC" -> "/a", "NETRC" -> "/b", "HOME" -> home.toString)
        assert(candidates(env, null, isWindows = false) == Seq(Paths.get("/a")))
        assert(candidates(env - "COURSIER_NETRC", null, isWindows = false) == Seq(Paths.get("/b")))
      }
      test("then HOME, then user.home") {
        val env = Map("HOME" -> home.toString)
        assert(candidates(env, "/other", isWindows = false) == Seq(home.resolve(".netrc")))
        assert(
          candidates(env, "/other", isWindows = true) ==
            Seq(home.resolve(".netrc"), home.resolve("_netrc"))
        )
        assert(candidates(
          Map.empty,
          home.toString,
          isWindows = false
        ) == Seq(home.resolve(".netrc")))
      }
    }

    test("read by bootstraps, after the other credentials") {
      val all = Credentials.credentials().asScala
      assert(all.lastOption == Some(new NetrcCredentials()))
    }
  }
}
