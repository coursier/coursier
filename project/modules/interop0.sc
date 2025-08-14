import $file.^.deps, deps.Deps
import $file.shared, shared.{CoursierPublishModule, CsCrossJvmJsModule, CsMima, CsModule}

import mill._, mill.scalalib._

trait Scalaz extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-scalaz-interop"
}

trait Cats extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-cats-interop"
  def ivyDeps      = super.ivyDeps() ++ Agg(
    Deps.catsEffect
  )
}
