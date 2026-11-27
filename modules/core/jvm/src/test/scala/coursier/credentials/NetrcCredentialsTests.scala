package coursier.credentials

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import utest._

object NetrcCredentialsTests extends TestSuite {

  private def entries(content: String): Seq[(String, String, String)] =
    NetrcCredentials.parse(content, "test").map { c =>
      (
        if (c.anyHost) "*" else c.host,
        c.usernameOpt.getOrElse(""),
        c.passwordOpt.fold("")(_.value)
      )
    }

  /** The credentials sent to `url`, picked the way the cache picks them */
  private def lookup(content: String, url: String): Option[(String, String)] =
    NetrcCredentials.parse(content, "test")
      .find(_.autoMatches(url, None))
      .map(c => (c.usernameOpt.getOrElse(""), c.passwordOpt.fold("")(_.value)))

  /** The credentials sent to `url` when the repository URL names `user` */
  private def lookupForUser(content: String, url: String, user: String): Option[String] =
    NetrcCredentials.parse(content, "test")
      .find(_.matches(url, user))
      .map(_.passwordOpt.fold("")(_.value))

  private def withTmpDir[T](f: Path => T): T = {
    val dir = Files.createTempDirectory("coursier-netrc-tests")
    try f(dir)
    finally {
      val stream = Files.walk(dir)
      try
        stream.sorted(java.util.Comparator.reverseOrder[Path]()).forEach(p => Files.delete(p))
      finally stream.close()
    }
  }

  private def write(path: Path, content: String): Path =
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))

  val tests = Tests {

    test("parse") {
      test("one line per entry") {
        val res = entries(
          """machine repo.example.com login alex password s3cret
            |machine other.example.com login bob password b0b
            |""".stripMargin
        )
        val expected = Seq(
          ("repo.example.com", "alex", "s3cret"),
          ("other.example.com", "bob", "b0b")
        )
        assert(res == expected)
      }

      test("tokens on several lines, no final new line") {
        val res = entries("machine repo.example.com\n  login alex\n\tpassword s3cret")
        assert(res == Seq(("repo.example.com", "alex", "s3cret")))
      }

      test("keywords are case-insensitive") {
        val res = entries("MACHINE repo.example.com Login alex PassWord s3cret")
        assert(res == Seq(("repo.example.com", "alex", "s3cret")))
      }

      test("keywords are fine as values") {
        val res = entries("machine login login password password machine")
        assert(res == Seq(("login", "password", "machine")))
      }

      test("quoted values") {
        val res = entries(
          """machine repo.example.com login "alex a" password "a \"b\"\tc\\d \e""""
        )
        assert(res == Seq(("repo.example.com", "alex a", "a \"b\"\tc\\d e")))
      }

      test("comments") {
        val res = entries(
          """# machine commented.example.com login a password b
            |   # indented comment
            |machine repo.example.com login alex password s3c#ret
            |""".stripMargin
        )
        assert(res == Seq(("repo.example.com", "alex", "s3c#ret")))
      }

      test("Windows new lines") {
        val res = entries("machine repo.example.com\r\nlogin alex\r\npassword s3cret\r\n")
        assert(res == Seq(("repo.example.com", "alex", "s3cret")))
      }

      test("macdef is skipped up to the next empty line") {
        val res = entries(
          """machine repo.example.com login alex password s3cret
            |macdef init
            |machine ignored.example.com login a password b
            |
            |machine other.example.com login bob password b0b
            |""".stripMargin
        )
        val expected = Seq(
          ("repo.example.com", "alex", "s3cret"),
          ("other.example.com", "bob", "b0b")
        )
        assert(res == expected)
      }

      test("account is ignored") {
        val res = entries("machine repo.example.com login alex account acc password s3cret")
        assert(res == Seq(("repo.example.com", "alex", "s3cret")))
      }

      test("the last login and password of an entry win") {
        val res = entries("machine repo.example.com login a password b login alex password s3cret")
        assert(res == Seq(("repo.example.com", "alex", "s3cret")))
      }

      test("values outside of an entry are ignored") {
        val res = entries("login alex password s3cret junk machine repo.example.com login bob")
        assert(res.isEmpty)
      }

      test("entries without login or password are ignored") {
        val res = entries(
          """machine a.example.com login alex
            |machine b.example.com password s3cret
            |machine c.example.com login bob password b0b
            |""".stripMargin
        )
        assert(res == Seq(("c.example.com", "bob", "b0b")))
      }

      test("default entry") {
        val res = entries(
          """machine repo.example.com login alex password s3cret
            |default login anon password an0n
            |""".stripMargin
        )
        val expected = Seq(
          ("repo.example.com", "alex", "s3cret"),
          ("*", "anon", "an0n")
        )
        assert(res == expected)
      }

      test("unterminated quote") {
        val content =
          """machine repo.example.com login alex password s3cret
            |machine other.example.com login bob password "b0b
            |""".stripMargin
        val ex = assertThrows[Exception](NetrcCredentials.parse(content, "/the/file"))
        assert(ex.getMessage.contains("/the/file"))
        assert(ex.getMessage.contains("unterminated"))
        assert(!ex.getMessage.contains("b0b"))

        val (before, errorOpt) = NetrcCredentials.parseEntries(content)
        assert(before.map(_.host) == Seq("repo.example.com"))
        assert(errorOpt.nonEmpty)
      }
    }

    test("matching") {
      val content =
        """machine repo.example.com login alex password s3cret
          |machine repo.example.com login bob password b0b
          |machine other.example.com login carol password car0l
          |""".stripMargin

      test("any scheme, any port") {
        for (
          url <- Seq(
            "https://repo.example.com/maven/a.pom",
            "https://repo.example.com:8443/maven/a.pom",
            "http://repo.example.com/maven/a.pom",
            "http://repo.example.com:8081/maven/a.pom"
          )
        ) {
          val res = lookup(content, url)
          assert(res == Some(("alex", "s3cret")))
        }
      }

      test("host names are case-insensitive") {
        val res = lookup(content, "https://REPO.Example.com/maven/a.pom")
        assert(res == Some(("alex", "s3cret")))
      }

      test("other hosts") {
        assert(lookup(content, "https://other.example.com/a.pom") == Some(("carol", "car0l")))
        assert(lookup(content, "https://unknown.example.com/a.pom").isEmpty)
        assert(lookup(content, "https://sub.repo.example.com/a.pom").isEmpty)
      }

      test("the user of the repository picks the entry") {
        val url = "https://repo.example.com/maven/a.pom"
        assert(lookupForUser(content, url, "bob") == Some("b0b"))
        assert(lookupForUser(content, url, "alex") == Some("s3cret"))
        assert(lookupForUser(content, url, "dave").isEmpty)
      }

      test("default entry") {
        val withDefault = content + "default login anon password an0n\n"
        val url         = "http://unknown.example.com:8081/a.pom"
        assert(lookup(withDefault, url) == Some(("anon", "an0n")))
        assert(lookup(withDefault, "https://repo.example.com/a.pom") == Some(("alex", "s3cret")))
        assert(lookupForUser(withDefault, url, "anon") == Some("an0n"))
        assert(lookupForUser(withDefault, url, "dave").isEmpty)
      }

      test("default entry first") {
        val res = lookup(
          "default login anon password an0n\n" + content,
          "https://repo.example.com/a.pom"
        )
        assert(res == Some(("anon", "an0n")))
      }

      test("only HTTP and HTTPS") {
        val res = lookup(content + "default login anon password an0n\n", "ftp://repo.example.com/a")
        assert(res.isEmpty)
      }
    }

    test("file") {
      val content = "machine repo.example.com login alex password s3cret\n"

      test("optional, missing") {
        withTmpDir { dir =>
          val creds = NetrcCredentials(Some(dir.resolve("netrc").toString))
          assert(creds.get().isEmpty)
        }
      }

      test("required, missing") {
        withTmpDir { dir =>
          val path  = dir.resolve("netrc").toString
          val creds = NetrcCredentials(Some(path), optional = false)
          val ex    = assertThrows[Exception](creds.get())
          assert(ex.getMessage.contains(path))
        }
      }

      test("a directory is a missing file") {
        withTmpDir { dir =>
          assert(NetrcCredentials(Some(dir.toString)).get().isEmpty)
          assertThrows[Exception](NetrcCredentials(Some(dir.toString), optional = false).get())
        }
      }

      test("read") {
        withTmpDir { dir =>
          val path = write(dir.resolve("netrc"), content).toString
          for (optional <- Seq(true, false)) {
            val res = NetrcCredentials(Some(path), optional).get().map(_.host)
            assert(res == Seq("repo.example.com"))
          }
        }
      }

      test("syntax error") {
        withTmpDir { dir =>
          val path = write(dir.resolve("netrc"), content + "machine b login \"bob\n").toString
          val res  = NetrcCredentials(Some(path)).get().map(_.host)
          assert(res == Seq("repo.example.com"))
          val ex = assertThrows[Exception](NetrcCredentials(Some(path), optional = false).get())
          assert(ex.getMessage.contains(path))
        }
      }
    }

    test("default file") {
      def candidates(env: Map[String, String], userHome: Option[String], isWindows: Boolean) =
        NetrcCredentials.defaultCandidates(env.get, userHome, isWindows)
      val home = Paths.get("/home/alex")

      test("COURSIER_NETRC first") {
        val env = Map("COURSIER_NETRC" -> "/a", "NETRC" -> "/b", "HOME" -> home.toString)
        assert(candidates(env, None, isWindows = true) == Seq(Paths.get("/a")))
      }
      test("then NETRC") {
        val env = Map("COURSIER_NETRC" -> "", "NETRC" -> "/b", "HOME" -> home.toString)
        assert(candidates(env, None, isWindows = true) == Seq(Paths.get("/b")))
      }
      test("then HOME") {
        val env = Map("HOME" -> home.toString)
        assert(candidates(env, Some("/other"), isWindows = false) == Seq(home.resolve(".netrc")))
        val windows = Seq(home.resolve(".netrc"), home.resolve("_netrc"))
        assert(candidates(env, Some("/other"), isWindows = true) == windows)
      }
      test("then user.home") {
        val res = candidates(Map.empty, Some(home.toString), isWindows = false)
        assert(res == Seq(home.resolve(".netrc")))
      }
      test("nothing") {
        assert(candidates(Map.empty, None, isWindows = false).isEmpty)
      }
    }
  }
}
