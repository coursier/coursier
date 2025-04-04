package coursier.tests

import java.math.BigInteger

import coursier.cache.{ArtifactError, CacheChecksum, FileCache}
import coursier.util.{Artifact, Gather, Sync, Task}
import utest._

import scala.concurrent.{ExecutionContext, Future}

object ChecksumTests extends TestSuite {
  val tests = Tests {

    test("parse") {

      def sha1ParseTest(clean: String, others: String*): Unit = {
        val expected = Some(new BigInteger(clean, 16))

        assert(CacheChecksum.parseChecksum(clean) == expected)
        for (other <- others)
          assert(CacheChecksum.parseChecksum(other) == expected)
      }

      test("junk") {
        // https://repo1.maven.org/maven2/org/apache/spark/spark-core_2.11/1.2.0/spark-core_2.11-1.2.0.pom.sha1
        // as of 2016-03-02
        val junkSha1 =
          "./spark-core_2.11/1.2.0/spark-core_2.11-1.2.0.pom:\n" +
            "5630 42A5 4B97 E31A F452  9EA0 DB79 BA2C 4C2B B6CC"

        val cleanSha1 = "563042a54b97e31af4529ea0db79ba2c4c2bb6cc"

        sha1ParseTest(cleanSha1, junkSha1)
      }

      test("singleLine") {
        // https://repo1.maven.org/maven2/org/json/json/20080701/json-20080701.pom.sha1
        // as of 2016-03-05
        val dirtySha1 =
          "4bf5daa95eb5c12d753a359a3e00621fdc73d187  " + // no CR here
            "/home/maven/repository-staging/to-ibiblio/maven2/org/json/json/20080701/json-20080701.pom"

        val cleanSha1 = "4bf5daa95eb5c12d753a359a3e00621fdc73d187"

        sha1ParseTest(cleanSha1, dirtySha1)
      }

      test("singleLineEndingWithChunkedSha1") {
        // http://www-eu.apache.org/dist/kafka/0.10.1.0/kafka_2.11-0.10.1.0.tgz.sha1
        // as of 2017-08-17
        val dirtySha1 =
          "kafka_2.11-0.10.1.0.tgz: 710F 31E7 0AB7 54BF D533  3278 E226 82C9 8DD0 56CA\n"

        val cleanSha1 = "710f31e70ab754bfd5333278e22682c98dd056ca"

        sha1ParseTest(cleanSha1, dirtySha1)
      }

      test("nonHexValue") {
        val content = "0000000000000000000000000000000z"
        val res     = CacheChecksum.parseChecksum(content)
        assert(res.isEmpty)
      }

      test("binarySha1") {
        val content = Platform.readFullySync(getClass.getResource("/empty.sha1").openStream())
        val res     = CacheChecksum.parseRawChecksum(content)
        assert(res.nonEmpty)
      }

      test("binarySha256") {
        val content = Platform.readFullySync(getClass.getResource("/empty.sha256").openStream())
        val res     = CacheChecksum.parseRawChecksum(content)
        assert(res.nonEmpty)
      }

      test("binarySha512") {
        val content = Platform.readFullySync(getClass.getResource("/empty.sha512").openStream())
        val res     = CacheChecksum.parseRawChecksum(content)
        assert(res.nonEmpty)
      }

      test("binaryMd5") {
        val content = Platform.readFullySync(getClass.getResource("/empty.md5").openStream())
        val res     = CacheChecksum.parseRawChecksum(content)
        assert(res.nonEmpty)
      }
    }

    test("artifact") {

      // not sure we should that directory as cache...
      val cache = HandmadeMetadata.repoBase

      def validate(artifact: Artifact, sumType: String): Task[Either[ArtifactError, Unit]] =
        FileCache()
          .noCredentials
          .withLocation(cache)
          .withPool(Sync.fixedThreadPool(4))
          .validateChecksum(artifact, sumType).run

      def artifact(url: String) = Artifact(
        url,
        Map(
          "MD5"     -> (url + ".md5"),
          "SHA-1"   -> (url + ".sha1"),
          "SHA-256" -> (url + ".sha256"),
          "SHA-512" -> (url + ".sha512")
        ),
        Map.empty,
        changing = false,
        optional = false,
        authentication = None
      )

      val artifacts = Seq(
        "http://abc.com/com/abc/test/0.1/test-0.1.pom",
        // corresponding SHA-1 starts with a 0
        "http://abc.com/com/github/alexarchambault/coursier_2.11/1.0.0-M9/coursier_2.11-1.0.0-M9.pom"
      ).map(artifact)

      def validateAll(sumType: String): Future[Unit] =
        Gather[Task].gather(
          artifacts.map { artifact =>
            validate(artifact, sumType).map { res =>
              assert(res.isRight)
            }
          }
        ).map(_ => ()).future()(ExecutionContext.global)

      test("sha1") {
        validateAll("SHA-1")
      }
      test("sha256") {
        validateAll("SHA-256")
      }
      test("sha512") {
        validateAll("SHA-512")
      }
      test("md5") {
        validateAll("MD5")
      }
    }
  }
}
