package coursier.cli.app

sealed abstract class LauncherType extends Product with Serializable {
  def needsBatOnWindows: Boolean = false
}

object LauncherType {

  case object Bootstrap extends LauncherType {
    override def needsBatOnWindows = true
  }
  case object Assembly extends LauncherType {
    override def needsBatOnWindows = true
  }
  case object Standalone extends LauncherType {
    override def needsBatOnWindows = true
  }
  case object GraalvmNativeImage extends LauncherType

  def parse(input: String): Either[String, LauncherType] =
    input match {
      case "bootstrap" => Right(Bootstrap)
      case "assembly" => Right(Assembly)
      case "standalone" => Right(Standalone)
      case "graalvm-native-image" => Right(GraalvmNativeImage)
      case _ => Left(s"Unrecognized launcher type: $input")
    }

}
