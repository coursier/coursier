package coursierbuild.modules

import coursierbuild.Deps.Deps
import com.github.lolgab.mill.mima._

import mill._, mill.scalalib._

trait CoursierTests extends TestModule {
  def ivyDeps = Task {
    super.ivyDeps() ++ Agg(
      Deps.diffUtils,
      Deps.pprint,
      Deps.scalaAsync
    )
  }
}
