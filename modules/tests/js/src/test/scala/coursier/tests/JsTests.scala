package coursier.tests

import coursier.maven.MavenRepository
import coursier.tests.compatibility._
import coursier.util.StringInterpolators._
import coursier.version.{Version, VersionConstraint}

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
        .find0(mod"ch.qos.logback:logback-classic", VersionConstraint("1.1.3"), Platform.artifact)
        .map {
          case (_, proj) =>
            val parent = proj.parent0
            assert(parent == Some(mod"ch.qos.logback:logback-parent", Version("1.1.3")))
        }
        .run
        .map { res =>
          val isRight = res.isRight
          assert(isRight)
        }
        .future()
    }
  }

}
