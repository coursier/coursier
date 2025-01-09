package coursier.cli.about

import caseapp.core.RemainingArgs
import coursier.cache.CacheDefaults
import coursier.cli.CoursierCommand
import coursier.jvm.JvmChannel

object About extends CoursierCommand[AboutOptions] {
  private def isGraalvmNativeImage: Boolean =
    sys.props.contains("org.graalvm.nativeimage.imagecode")

  def run(options: AboutOptions, args: RemainingArgs): Unit = {
    val launcherType =
      if (isGraalvmNativeImage) "native"
      else "jar"

    println(s"Launcher type: $launcherType")

    if (!isGraalvmNativeImage) {
      val vendorItems =
        sys.props.get("java.vendor").toSeq ++ sys.props.get("java.vendor.version").toSeq
      val vendorPart =
        if (vendorItems.isEmpty) ""
        else s" (${vendorItems.mkString(" ")})"
      println(
        s"JVM: ${sys.props.getOrElse("java.vm.name", "")} ${sys.props.getOrElse("java.version", "")}$vendorPart"
      )
      println(s"Java version: ${sys.props.getOrElse("java.version", "")}")
    }

    println(s"Cache location: ${CacheDefaults.location}")
    println(s"Archive cache location: ${CacheDefaults.archiveCacheLocation}")

    println(s"OS: ${JvmChannel.currentOs.fold("Error: " + _, x => x)}")
    println(s"CPU architecture: ${JvmChannel.currentArchitecture.fold("Error: " + _, x => x)}")
  }
}
