package coursierbuild.modules

import coursierbuild.Deps.Deps
import com.github.lolgab.mill.mima._

import mill._, mill.scalalib._

trait CoursierTests extends TestModule with ScalaModule with CsTests {
  def mvnDeps = Task {
    super.mvnDeps() ++ Seq(
      Deps.diffUtils,
      Deps.pprint
    )
  }

  def defaultTask() = super[TestModule].defaultTask()
}
