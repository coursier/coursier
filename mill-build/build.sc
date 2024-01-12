import mill._
import mill.main.BuildInfo
import mill.scalalib._

object root extends mill.runner.MillBuildRootModule {
  override def ivyDeps = Agg(
    ivy"com.lihaoyi::mill-contrib-bloop:${BuildInfo.millVersion}",
    ivy"com.github.lolgab::mill-mima::0.1.0",
    ivy"io.get-coursier.util::get-cs:0.1.1",
    ivy"com.softwaremill.sttp.client::core:2.3.0",
    ivy"io.get-coursier::coursier-launcher:2.1.7",
    ivy"io.github.alexarchambault.mill::mill-native-image::0.1.26",
    ivy"org.jsoup:jsoup:1.17.2",
    // Newer versions are API incompatible
    // 1.9.0 -> 1.10.0 -> 1.11.0 -> 1.12.0 -> 1.13.0 -> 1.13.1
    ivy"com.eed3si9n.jarjarabrams::jarjar-abrams-core:1.9.0"
  )
}
