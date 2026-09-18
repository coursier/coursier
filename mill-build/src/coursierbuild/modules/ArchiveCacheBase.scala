package coursierbuild.modules

import coursierbuild.Deps
import mill._
import com.github.lolgab.mill.mima._

trait ArchiveCacheBase extends CsModule with CsCrossJvmModule with CoursierPublishModule
    with CsMima {
  def artifactName = "coursier-archive-cache"
}
