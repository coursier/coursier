package coursier
package test

import coursier.test.compatibility._

import utest._

import scala.concurrent.{ Future, Promise }

object JsTests extends TestSuite {

  val tests = Tests {
    'promise{
      val p = Promise[Unit]()
      Future(p.success(()))
      p.future
    }

    'get{
      Platform.get("https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.1.3/logback-classic-1.1.3.pom")
        .map(core.compatibility.xmlParseDom)
        .map{ xml =>
          assert(xml.toOption.exists(_.label == "project"))
        }
    }

    'getProj{
      MavenRepository("https://repo1.maven.org/maven2/")
        .find(mod"ch.qos.logback:logback-classic", "1.1.3", Platform.artifact)
        .map{case (_, proj) =>
          assert(proj.parent == Some(mod"ch.qos.logback:logback-parent", "1.1.3"))
        }
        .run
        .map { res =>
          assert(res.isRight)
        }
        .future()
    }
  }

}
