package coursier
package test

import java.io.File

import coursier.cache.protocol.TestprotocolHandler

import utest._

import scala.util.Try

object CacheFetchTests extends TestSuite {

  val tests = TestSuite {

    * - {
      // test that everything's fine with basic file protocol
      val repoPath = new File(getClass.getResource("/test-repo/http/abc.com").getPath)
      Util.check(MavenRepository(repoPath.toURI.toString))
    }

    'customProtocol - {
      * - {
        // test the Cache.url method
        val shouldFail = Try(Cache.url("notfoundzzzz://foo/bar"))
        assert(shouldFail.isFailure)

        Cache.url("testprotocol://foo/bar")
      }

      * - {
        // the real custom protocol test
        Util.check(MavenRepository(s"${TestprotocolHandler.protocol}://foo/"))
      }
    }
  }

}
