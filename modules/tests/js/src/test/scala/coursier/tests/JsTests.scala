package coursier.tests

import coursier.maven.MavenRepository
import coursier.tests.compatibility._
import coursier.util.StringInterpolators._

import utest._

import scala.concurrent.{Future, Promise}

object JsTests extends TestSuite {

  val tests = Tests {
    test("promise") {
      val p = Promise[Unit]()
      Future(p.success(()))
      p.future
    }

    test("get") {
      coursier.cache.internal.Platform.get(
        "https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.1.3/logback-classic-1.1.3.pom"
      )
        .map(coursier.core.compatibility.xmlParseDom)
        .map { xml =>
          assert(xml.toOption.exists(_.label == "project"))
        }
    }

    test("getProj") {
      MavenRepository("https://repo1.maven.org/maven2/")
        .find(mod"ch.qos.logback:logback-classic", "1.1.3", Platform.artifact)
        .map { case (_, proj) =>
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
