package coursier
package test

import java.io.File
import java.nio.file.Files

import coursier.cache.{CacheUrl, FileCache}
import coursier.cache.protocol.TestprotocolHandler
import coursier.util.Task
import utest._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.util.Try

object CacheFetchTests extends TestSuite {

  def check(
    extraRepo: Repository,
    followHttpToHttpsRedirections: Boolean = false,
    deps: Seq[Dependency] = Seq(Dependency(mod"com.github.alexarchambault:coursier_2.11", "1.0.0-M9-test")),
    addCentral: Boolean = true
  ): Unit = {

    val tmpDir = Files.createTempDirectory("coursier-cache-fetch-tests").toFile

    def cleanTmpDir() = {
      def delete(f: File): Boolean =
        if (f.isDirectory) {
          val removedContent = Option(f.listFiles()).toSeq.flatten.map(delete).forall(x => x)
          val removedDir = f.delete()

          removedContent && removedDir
        } else
          f.delete()

      if (!delete(tmpDir))
        Console.err.println(s"Warning: unable to remove temporary directory $tmpDir")
    }

    val fetchs = FileCache()
      .copy(
        location = tmpDir,
        followHttpToHttpsRedirections = followHttpToHttpsRedirections
      )
      .fetchs

    val processFetch = ResolutionProcess.fetch(
      Seq(
        extraRepo
      ) ++ {
        if (addCentral)
          Seq(MavenRepository("https://repo1.maven.org/maven2"))
        else
          Nil
      },
      fetchs.head,
      fetchs.tail: _*
    )

    val startRes = Resolution(deps)

    val f = startRes
      .process
      .run(processFetch)
      .future()(ExecutionContext.global)

    val res =
      try Await.result(f, Duration.Inf)
      finally {
        cleanTmpDir()
      }

    val errors = res.errors

    assert(errors.isEmpty)
  }

  val tests = Tests {

    // using scala-test would allow to put the below comments in the test names...

    * - {
      // test that everything's fine with basic file protocol
      val f = new File(HandmadeMetadata.repoBase, "http/abc.com").getAbsoluteFile
      check(MavenRepository(f.toURI.toString))
    }

    'customProtocol - {
      * - {
        // test the Cache.url method
        val shouldFail = Try(CacheUrl.url("notfoundzzzz://foo/bar"))
        assert(shouldFail.isFailure)

        CacheUrl.url("testprotocol://foo/bar")
      }

      * - {
        // the real custom protocol test
        check(MavenRepository(s"${TestprotocolHandler.protocol}://foo/"))
      }
    }
  }

}
