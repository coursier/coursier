package coursier

import java.io.File

import coursier.params.MirrorConfFile
import utest._

import scala.async.Async.{async, await}

object MirrorConfFileTests extends TestSuite {

  import TestHelpers.{ec, cache, validateDependencies}

  val tests = Tests {
    'read - {
      val path = Option(getClass.getResource("/empty-mirror.properties"))
        .map(u => new File(u.toURI).getAbsolutePath)
        .getOrElse {
          throw new Exception("empty-mirror.properties resource not found")
        }
      val f = MirrorConfFile(path)
      val mirrors = f.mirrors()
      val expectedMirrors = Seq()
      assert(mirrors == expectedMirrors)
    }


    'resolve - {

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

      * - {
        val mirrorFilePath = Option(getClass.getResource("/test-mirror.properties"))
          .map(u => new File(u.toURI).getAbsolutePath)
          .getOrElse {
            throw new Exception("test-mirror.properties resource not found")
          }
        run(MirrorConfFile(mirrorFilePath))
      }
    }
  }

}
