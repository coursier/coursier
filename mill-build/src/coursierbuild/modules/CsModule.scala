package coursierbuild.modules

import mill._, mill.scalalib._
import mill.scalalib.api.JvmWorkerUtil

trait CsModule extends SbtModule with CsScalaModule with CoursierJavaModule {
  def sources = T.sources {
    val sbv    = JvmWorkerUtil.scalaBinaryVersion(scalaVersion())
    val parent = super.sources()
    val extra = parent.map(_.path).filter(_.last == "scala").flatMap { p =>
      val dirNames = Seq(s"scala-$sbv")
      dirNames.map(n => PathRef(p / os.up / n))
    }
    parent ++ extra
  }
}
