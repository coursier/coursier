package coursier.cache.server

import utest._

object UrlFilterTests extends TestSuite {

  private val central = "https://repo1.maven.org/maven2"
  private val pomUrl  = s"$central/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.pom"

  val tests = Tests {

    test("allow all") {
      assert(UrlFilter.allowAll.isAllowed(pomUrl))
      assert(UrlFilter.allowAll.isAllowed("file:/etc/passwd"))
      assert(UrlFilter.allowAll.isAllowed(s"$central/org/../../a"))
    }

    test("disallow") {
      val filter = new UrlFilter(defaultAllow = true, Nil, Seq("file:", "http://169.254.*"))
      assert(filter.isAllowed(pomUrl))
      assert(!filter.isAllowed("file:/etc/passwd"))
      assert(!filter.isAllowed("file:///etc/passwd"))
      assert(!filter.isAllowed("http://169.254.169.254/latest/meta-data/"))
      assert(filter.isAllowed("http://169.25.0.1/a"))
    }

    test("default disallow") {
      val filter = new UrlFilter(defaultAllow = false, Seq(central), Nil)
      assert(filter.isAllowed(pomUrl))
      assert(filter.isAllowed(central))
      assert(!filter.isAllowed("https://repo.maven.apache.org/maven2/a/b"))
      assert(!filter.isAllowed("file:/etc/passwd"))
    }

    test("prefixes stop at path segments") {
      val filter = new UrlFilter(defaultAllow = false, Seq("https://repo1.maven.org"), Nil)
      assert(filter.isAllowed(pomUrl))
      assert(!filter.isAllowed("https://repo1.maven.org.example.com/a"))
      assert(!filter.isAllowed("https://repo1.maven.org:8443/a"))

      val withSlash = new UrlFilter(defaultAllow = false, Seq(s"$central/"), Nil)
      assert(withSlash.isAllowed(pomUrl))
      assert(!withSlash.isAllowed(s"$central-other/a"))
    }

    test("allow and disallow combined") {
      val orgPrefix = s"$central/org/scala-lang"

      val defaultAllow = new UrlFilter(defaultAllow = true, Seq(orgPrefix), Seq(central))
      assert(defaultAllow.isAllowed(pomUrl))
      assert(!defaultAllow.isAllowed(s"$central/com/a/b"))
      assert(defaultAllow.isAllowed("https://example.com/a"))

      val defaultDisallow = new UrlFilter(defaultAllow = false, Seq(central), Seq(orgPrefix))
      assert(!defaultDisallow.isAllowed(pomUrl))
      assert(defaultDisallow.isAllowed(s"$central/com/a/b"))
      assert(!defaultDisallow.isAllowed("https://example.com/a"))
    }

    test("scheme and host are compared ignoring case") {
      val disallowing = new UrlFilter(defaultAllow = true, Nil, Seq("file:", "http://metadata"))
      assert(!disallowing.isAllowed("FILE:/etc/passwd"))
      assert(!disallowing.isAllowed("File:///etc/passwd"))
      assert(!disallowing.isAllowed("HTTP://METADATA/computeMetadata"))

      val allowing = new UrlFilter(defaultAllow = false, Seq(s"$central/org/scala-lang"), Nil)
      assert(allowing.isAllowed("HTTPS://REPO1.MAVEN.ORG/maven2/org/scala-lang/a"))
      // paths stay case-sensitive
      assert(!allowing.isAllowed(s"$central/org/Scala-Lang/a"))
    }

    test("dot segments are rejected") {
      val filter = new UrlFilter(defaultAllow = false, Seq(s"$central/org/scala-lang"), Nil)
      assert(!filter.isAllowed(s"$central/org/scala-lang/../../../a"))
      assert(!filter.isAllowed(s"$central/org/scala-lang/%2e%2e/a"))
      assert(!filter.isAllowed(s"$central/org/scala-lang/%2E%2E/a"))
      assert(!filter.isAllowed(s"$central/org/scala-lang/./a"))
      assert(!filter.isAllowed(s"$central/org/scala-lang/..\\..\\a"))
      assert(filter.isAllowed(s"$central/org/scala-lang/a..b/c"))

      val disallowing = new UrlFilter(defaultAllow = true, Nil, Seq("file:/home"))
      assert(!disallowing.isAllowed("file:/tmp/../home/user/.ssh/id_rsa"))
    }
  }
}
