
import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt._
import sbt.Keys._

object Publish {

  lazy val dontPublish = Seq(
    publish := {},
    publishLocal := {},
    publishArtifact := false
  )

  def onlyPublishIn(sbv: String) = Seq(
    publishArtifact := {
      val sbv0 = CrossVersion.partialVersion(scalaBinaryVersion.value).fold("") {
        case (maj, min) => s"$maj.$min"
      }
      sbv == sbv0 && publishArtifact.value
    }
  )

  def dontPublishScalaJsIn(sbv: String*) = Seq(
    publishArtifact := {
      (!ScalaJSPlugin.autoImport.isScalaJSProject.value || !sbv.contains(scalaBinaryVersion.value)) &&
        publishArtifact.value
    }
  )

}
