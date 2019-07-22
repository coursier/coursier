package coursier.cli.app

sealed abstract class LauncherType extends Product with Serializable {
  def needsBatOnWindows: Boolean = false
  def isExeOnWindows: Boolean = false
}

object LauncherType {

  case object Bootstrap extends LauncherType {
    override def needsBatOnWindows = true
  }
  case object Assembly extends LauncherType {
    override def needsBatOnWindows = true
  }
  case object Hybrid extends LauncherType {
    override def needsBatOnWindows = true
  }
  case object Standalone extends LauncherType {
    override def needsBatOnWindows = true
  }
  case object ScalaNative extends LauncherType {
    override def isExeOnWindows: Boolean = true
  }
  case object GraalvmNativeImage extends LauncherType {
    override def isExeOnWindows: Boolean = true
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
