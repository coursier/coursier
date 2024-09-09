package coursier.tests

import java.io.File
import java.nio.file.{Path, Paths}

import coursier.Resolve
import coursier.params.MirrorConfFile
import coursier.util.StringInterpolators._
import utest._

import scala.async.Async.{async, await}

object MirrorTests extends TestSuite {

  import TestHelpers.{ec, cache, validateDependencies}

  val tests = Tests {
    test("read") {
      val path = Option(getClass.getResource("/empty-mirror.properties"))
        .map(u => new File(u.toURI).getAbsolutePath)
        .getOrElse {
          throw new Exception("empty-mirror.properties resource not found")
        }
      val f               = MirrorConfFile(path)
      val mirrors         = f.mirrors()
      val expectedMirrors = Seq()
      assert(mirrors == expectedMirrors)
    }

    test("resolve") {

      def run(file: MirrorConfFile) = async {
        val res = await {
          Resolve()
            .noMirrors
            .withCache(cache)
            .addMirrorConfFiles(file)
            .addDependencies(dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M10")
            .future()
        }

        await(validateDependencies(res))

        val artifacts = res.artifacts()
        assert(artifacts.forall(_.url.startsWith("https://jcenter.bintray.com")))
      }

      test("property file") {
        val mirrorFilePath = Option(getClass.getResource("/test-mirror.properties"))
          .map(u => new File(u.toURI).getAbsolutePath)
          .getOrElse {
            throw new Exception("test-mirror.properties resource not found")
          }
        run(MirrorConfFile(mirrorFilePath))
      }

      def runConfFile(file: Path) = async {
        val res = await {
          Resolve()
            .noMirrors
            .withCache(cache)
            .addConfFiles(file)
            .addDependencies(dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M10")
            .future()
        }

        await(validateDependencies(res))

        val artifacts = res.artifacts()
        assert(artifacts.forall(_.url.startsWith("https://jcenter.bintray.com")))
      }

      test("conf file") {
        val confFilePath = Option(getClass.getResource("/test-mirror.json"))
          .map(u => Paths.get(u.toURI))
          .getOrElse {
            throw new Exception("test-mirror.json resource not found")
          }
        runConfFile(confFilePath)
      }
    }
  }

}
