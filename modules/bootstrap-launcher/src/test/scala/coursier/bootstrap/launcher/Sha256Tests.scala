package coursier.bootstrap.launcher

import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Collections

import utest._

import coursier.paths.CachePath

import scala.jdk.CollectionConverters._

object Sha256Tests extends TestSuite {

  private val abc       = "abc".getBytes(StandardCharsets.UTF_8)
  private val abcSha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

  val tests = Tests {

    test("sha256") {
      assert(Util.sha256(abc) == abcSha256)
      val f = Files.createTempFile("coursier-sha256-", ".bin")
      try {
        Files.write(f, abc)
        assert(Util.sha256(f.toFile) == abcSha256)
      }
      finally Files.deleteIfExists(f)
    }

    test("checkSha256") {
      Util.checkSha256(null, abcSha256, "https://foo/a.jar", "a.jar")
      Util.checkSha256("", abcSha256, "https://foo/a.jar", "a.jar")
      Util.checkSha256(abcSha256, abcSha256, "https://foo/a.jar", "a.jar")
      Util.checkSha256(abcSha256.toUpperCase, abcSha256, "https://foo/a.jar", "a.jar")
      val ex = assertThrows[IOException] {
        Util.checkSha256(abcSha256, "0" * 64, "https://foo/a.jar", "a.jar")
      }
      assert(ex.getMessage.contains("SHA-256 mismatch"))
      assert(ex.getMessage.contains("https://foo/a.jar"))
    }

    test("cached file with matching digest") {
      val cache = Files.createTempDirectory("coursier-sha256-cache-")
      try {
        val url  = "https://repo.invalid/a/b/c.jar"
        val dest = CachePath.localFile(url, cache.toFile, null, false).toPath
        Files.createDirectories(dest.getParent)
        Files.write(dest, abc)
        val download = new Download(1, cache.toFile, Collections.emptyList())
        // missing and empty digests aren't checked
        val urls = Seq(new URL(url), new URL(url), new URL(url))
        val res  = download.getLocalURLs(urls.asJava, Array(abcSha256, "")).asScala
        assert(res == Seq.fill(3)(dest.toUri.toURL))
      }
      finally
        Files.walk(cache).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
    }
  }

}
