package coursierbuild.modules

import com.github.lolgab.mill.mima.*
import coursierbuild.Deps.Deps
import mill.*

trait ArchiveCacheBase extends CsModule with CsCrossJvmJsModule with CoursierPublishModule
    with CsMima {
  def artifactName = "coursier-archive-cache"
  def compileMvnDeps = Seq(
    Deps.dataClass
  )
}
