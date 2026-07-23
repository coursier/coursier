package coursierbuild.modules

import coursierbuild.Deps.Deps
import com.github.lolgab.mill.mima._

import mill._, mill.scalalib._

trait CoursierTests extends TestModule with ScalaModule with CsTests {
  def mvnDeps = Task {
    super.mvnDeps() ++ Seq(
      Deps.diffUtils,
      Deps.pprint
    ) ++
      // scala-async is a Scala 2-only macro library (no Scala 3 artifact)
      (if (scalaVersion().startsWith("2.")) Seq(Deps.scalaAsync) else Nil)
  }

  def defaultTask() = super[TestModule].defaultTask()
}
