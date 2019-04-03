package coursier

import coursier.params.MirrorConfFile
import utest._

object MirrorConfFileTests extends TestSuite {

  val tests = Tests {
    'simple - {
      val path = Option(getClass.getResource("/test-mirror.properties"))
        .map(_.getPath)
        .getOrElse {
          throw new Exception("test-mirror.properties resource not found")
        }
      val f = MirrorConfFile(path)
      val mirrors = f.mirrors()
      val expectedMirrors = Seq()
      assert(mirrors == expectedMirrors)
    }
  }

}
