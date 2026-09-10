package coursier.cli

import caseapp.core.help.{Help, HelpFormat}
import caseapp.core.parser.Parser
import coursier.cache.FileCache
import coursier.cli.options.CacheOptions
import coursier.cli.params.CacheParams
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

    // the format repositories ask for, see https://central.sonatype.org/faq/429-tooling-provider/
    test("the help spells out the user agent format") {
      val help = Help[CacheOptions].help(HelpFormat.default())
      assert(help.contains("Coursier/2.1 (contact: ops@example.com)"))
      // the default the help claims has to be the one actually sent
      assert(help.contains(coursier.cache.CacheUrl.userAgent("Coursier", "cli")))
    }

    test("extra comment tokens land after the contact, in order") {
      assert(
        coursier.cache.CacheUrl.userAgent("Coursier", "ci", "json") ==
          "Coursier/2.1 (+https://github.com/coursier; ci; json)"
      )
    }
  }
}
