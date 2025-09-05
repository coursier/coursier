package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._
import com.github.lolgab.mill.mima._

trait ArchiveCacheBase extends CsModule with CsCrossJvmJsModule with CoursierPublishModule
    with CsMima {
  def artifactName   = "coursier-archive-cache"
  def compileIvyDeps = Agg(
    Deps.dataClass
  )
}
