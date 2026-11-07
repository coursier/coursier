package coursier.cli

import caseapp.core.help.{Help, HelpFormat}
import caseapp.core.parser.Parser
import coursier.cache.FileCache
import coursier.cli.options.CacheOptions
import coursier.cli.params.CacheParams
import coursier.credentials.{DirectCredentials, NetrcCredentials}
import coursier.util.Task
import utest._

object CacheParamsTests extends TestSuite {

  private def parse(args: String*): CacheOptions =
    Parser[CacheOptions].parse(args.toVector) match {
      case Left(err)        => sys.error(err.message)
      case Right((opts, _)) => opts
    }

  private def params(opts: CacheOptions): CacheParams =
    opts.params.fold(e => sys.error(e.toList.mkString(", ")), identity)

  val tests = Tests {

    test("--user-agent is passed down to the cache") {
      val params0 = params(parse("--user-agent", "Custom/1.2"))
      assert(params0.userAgent == Some("Custom/1.2"))

      params0.cache(coursier.cache.CacheDefaults.pool, coursier.cache.CacheLogger.nop) match {
        case fc: FileCache[Task] => assert(fc.userAgent == Some("Custom/1.2"))
        case other               => sys.error(s"Expected a FileCache, got $other")
      }
    }

    test("no user agent by default") {
      assert(params(parse()).userAgent.isEmpty)
    }

    test("cli runs say so in the comment") {
      val params0 = params(parse())
      assert(params0.userAgentComments == Seq("cli"))

      params0.cache(coursier.cache.CacheDefaults.pool, coursier.cache.CacheLogger.nop) match {
        case fc: FileCache[Task] =>
          assert(fc.userAgent == Some(coursier.cache.CacheUrl.coursierUserAgent("cli")))
          assert(fc.userAgent.exists(_.endsWith("; cli)")))
        case other => sys.error(s"Expected a FileCache, got $other")
      }
    }

    test("a blank user agent is ignored") {
      assert(params(parse("--user-agent", "  ")).userAgent.isEmpty)
    }

    test("--reject-non-authoritative-responses is passed down to the cache") {
      val params0 = params(parse("--reject-non-authoritative-responses"))
      assert(params0.rejectNonAuthoritativeResponses)

      params0.cache(coursier.cache.CacheDefaults.pool, coursier.cache.CacheLogger.nop) match {
        case fc: FileCache[Task] => assert(fc.rejectNonAuthoritativeResponses)
        case other               => sys.error(s"Expected a FileCache, got $other")
      }
    }

    test("--fail-203 is an alias for it") {
      assert(params(parse("--fail-203")).rejectNonAuthoritativeResponses)
    }

    test("203 responses are cached by default") {
      val params0 = params(parse())
      assert(!params0.rejectNonAuthoritativeResponses)

      params0.cache(coursier.cache.CacheDefaults.pool, coursier.cache.CacheLogger.nop) match {
        case fc: FileCache[Task] => assert(!fc.rejectNonAuthoritativeResponses)
        case other               => sys.error(s"Expected a FileCache, got $other")
      }
    }

    // the cache retries network errors (connect timeouts, 5xx, …) that many times, so the CLI
    // shouldn't cut that down to a single attempt unless asked to
    test("downloads get the cache's default retry count") {
      val params0 = params(parse())
      assert(params0.retryCount == coursier.cache.CacheDefaults.retryCount)

      params0.cache(coursier.cache.CacheDefaults.pool, coursier.cache.CacheLogger.nop) match {
        case fc: FileCache[Task] => assert(fc.retry == coursier.cache.CacheDefaults.retryCount)
        case other               => sys.error(s"Expected a FileCache, got $other")
      }
    }

    test("--retry-count is passed down to the cache") {
      val params0 = params(parse("--retry-count", "2"))
      assert(params0.retryCount == 2)

      params0.cache(coursier.cache.CacheDefaults.pool, coursier.cache.CacheLogger.nop) match {
        case fc: FileCache[Task] => assert(fc.retry == 2)
        case other               => sys.error(s"Expected a FileCache, got $other")
      }
    }

    test("a non-positive --retry-count is rejected") {
      assert(parse("--retry-count", "0").params.isInvalid)
    }

    test("the help states the actual default retry count") {
      val help = Help[CacheOptions].help(HelpFormat.default(), showHidden = true)
      assert(help.contains(s"default: ${coursier.cache.CacheDefaults.defaultRetryCount},"))
    }

    // the format repositories ask for, see https://central.sonatype.org/faq/429-tooling-provider/
    test("the help spells out the user agent format") {
      val help = Help[CacheOptions].help(HelpFormat.default())
      assert(help.contains("Coursier/2.1 (contact: ops@example.com)"))
      // the default the help claims has to be the one actually sent
      assert(help.contains(coursier.cache.CacheUrl.userAgent("Coursier", "cli")))
    }

    test("--checksum is passed down to the cache") {
      // "sha256" has to become "SHA-256", the key of Artifact#checksumUrls, or SHA-256
      // checksums are never fetched, and a strict list rejects every artifact
      val params0 = params(parse("--checksum", "sha256,SHA-1"))
      assert(params0.checksum == Seq(Some("SHA-256"), Some("SHA-1")))

      params0.cache(coursier.cache.CacheDefaults.pool, coursier.cache.CacheLogger.nop) match {
        case fc: FileCache[Task] => assert(fc.checksums == Seq(Some("SHA-256"), Some("SHA-1")))
        case other               => sys.error(s"Expected a FileCache, got $other")
      }

      assert(params(parse("--checksum", "SHA-256", "--checksum", "none")).checksum ==
        Seq(Some("SHA-256"), None))
    }

    test("checksums default to the cache's") {
      assert(params(parse()).checksum == coursier.cache.CacheDefaults.checksums)
    }

    test("--checksum is shown in the help") {
      val help = Help[CacheOptions].help(HelpFormat.default())
      assert(help.contains("--checksum"))
      assert(help.contains("COURSIER_CHECKSUMS"))
    }

    test("extra comment tokens land after the contact, in order") {
      assert(
        coursier.cache.CacheUrl.userAgent("Coursier", "ci", "json") ==
          "Coursier/2.1 (+https://github.com/coursier; ci; json)"
      )
    }

    test("netrc") {
      def netrc(args: String*): Option[NetrcCredentials] =
        params(parse(args*)).netrc
      def invalid(args: String*): Boolean =
        parse(args*).params.isInvalid

      test("read by default, optional") {
        assert(netrc() == Some(NetrcCredentials(None, optional = true)))
      }
      def withNetrcFile[T](content: String)(f: String => T): T = {
        val file = java.nio.file.Files.createTempFile("coursier-netrc", ".txt")
        try {
          java.nio.file.Files.write(file, content.getBytes(java.nio.charset.StandardCharsets.UTF_8))
          f(file.toString)
        }
        finally java.nio.file.Files.delete(file)
      }

      test("--netrc and --netrc-file make it mandatory") {
        withNetrcFile("machine a login b password c\n") { path =>
          val expected = Some(NetrcCredentials(Some(path), optional = false))
          assert(netrc("--netrc-file", path) == expected)
          assert(netrc("--netrc", "--netrc-file", path) == expected)
        }
      }
      test("a mandatory file that's missing or malformed is an error") {
        assert(invalid("--netrc-file", "/nonexistent/netrc"))
        withNetrcFile("machine a login b password \"c\n") { path =>
          assert(invalid("--netrc-file", path))
          assert(!invalid("--netrc-file", path, "--netrc-optional"))
        }
      }
      test("--netrc-file and --netrc-optional") {
        assert(
          netrc("--netrc-file", "/a", "--netrc-optional") ==
            Some(NetrcCredentials(Some("/a"), optional = true))
        )
      }
      test("--netrc=false") {
        assert(netrc("--netrc=false").isEmpty)
      }
      test("contradictions") {
        assert(invalid("--netrc=false", "--netrc-optional"))
        assert(invalid("--netrc=false", "--netrc-file", "/a"))
        assert(invalid("--netrc", "--netrc-optional"))
      }
      test("comes after the other credentials") {
        val params0 = params(parse("--credentials", "repo.example.com alex:pass"))
        params0.cache(coursier.cache.CacheDefaults.pool, coursier.cache.CacheLogger.nop) match {
          case fc: FileCache[Task] =>
            val passed = fc.credentials.collect {
              case c: DirectCredentials if c.host == "repo.example.com" => c
            }
            assert(passed.nonEmpty)
            assert(fc.credentials.last == NetrcCredentials())
          case other => sys.error(s"Expected a FileCache, got $other")
        }
      }
      test("not with --netrc=false") {
        val params0 = params(parse("--netrc=false"))
        params0.cache(coursier.cache.CacheDefaults.pool, coursier.cache.CacheLogger.nop) match {
          case fc: FileCache[Task] =>
            assert(!fc.credentials.contains(NetrcCredentials()))
          case other => sys.error(s"Expected a FileCache, got $other")
        }
      }
    }
  }
}
