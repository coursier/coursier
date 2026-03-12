package coursierbuild.modules

import coursierbuild.Deps.Deps
import com.github.lolgab.mill.mima._

import mill._, mill.scalalib._

trait CoursierTests extends TestModule with JavaModule with CsTests {
  def mvnDeps = Task {
    super.mvnDeps() ++ Seq(
      Deps.diffUtils,
      Deps.pprint,
      Deps.scalaAsync
    )
  }

  def defaultTask() = super[TestModule].defaultTask()
}
