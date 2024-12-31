import mill._
import mill.api.BuildInfo
import mill.runner.MillBuildRootModule
import mill.scalalib._

object `package` extends MillBuildRootModule {
  override def ivyDeps = Agg(
    ivy"com.lihaoyi::mill-contrib-bloop:${BuildInfo.millVersion}",
    ivy"com.github.lolgab::mill-mima::0.1.1",
    ivy"io.get-coursier.util::get-cs:0.1.1",
    ivy"com.softwaremill.sttp.client::core:2.3.0",
    ivy"io.get-coursier::coursier-launcher:2.1.7",
    ivy"io.github.alexarchambault.mill::mill-native-image::0.1.26",
    ivy"org.jsoup:jsoup:1.18.3",
    ivy"com.eed3si9n.jarjarabrams::jarjar-abrams-core:1.14.0"
  )
}
