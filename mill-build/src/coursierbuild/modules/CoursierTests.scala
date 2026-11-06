package coursierbuild.modules

import coursierbuild.Deps
import com.github.lolgab.mill.mima._

import mill._, mill.scalalib._
import mill.scalajslib.ScalaJSModule

trait CoursierTests extends TestModule with ScalaModule with CsTests {
  def mvnDeps = Task {
    // a Java library, only used by the JVM tests (that it's published for)
    val diffUtils = this match {
      case _: ScalaJSModule => Nil
      case _                => Seq(Deps.diffUtils)
    }
    super.mvnDeps() ++ diffUtils ++ Seq(Deps.pprint)
  }

  def defaultTask() = super[TestModule].defaultTask()
}
