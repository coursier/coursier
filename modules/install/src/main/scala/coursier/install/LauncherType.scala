package coursier.install

sealed abstract class LauncherType extends Product with Serializable {
  def isNative: Boolean = false
}

object LauncherType {

  sealed abstract class BootstrapLike extends LauncherType

  case object Bootstrap extends BootstrapLike
  case object Hybrid extends BootstrapLike
  case object Standalone extends BootstrapLike

  case object Assembly extends LauncherType
  case object ScalaNative extends LauncherType {
    override def isNative: Boolean = true
  }
  case object GraalvmNativeImage extends LauncherType {
    override def isNative: Boolean = true
  }

  /** Dummy generator, simply creating an empty JAR */
  case object DummyJar extends LauncherType
  /** Dummy generator, simply creating an empty file */
  case object DummyNative extends LauncherType {
    override def isNative: Boolean = true
  }

  def parse(input: String): Either[String, LauncherType] =
    input match {
      case "bootstrap" => Right(Bootstrap)
      case "assembly" => Right(Assembly)
      case "hybrid" => Right(Hybrid)
      case "standalone" => Right(Standalone)
      case "scala-native" => Right(ScalaNative)
      case "graalvm-native-image" => Right(GraalvmNativeImage)
      case _ => Left(s"Unrecognized launcher type: $input")
    }

}
