package coursierbuild.modules

import com.github.lolgab.mill.mima.*
import coursierbuild.Deps.Deps
import mill.*
import mill.scalalib.*

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
