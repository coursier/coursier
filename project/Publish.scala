
import com.typesafe.tools.mima.plugin.MimaKeys.mimaPreviousArtifacts
import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt._
import sbt.Keys._

object Publish {

  lazy val dontPublish = Seq(
    publish := {},
    publishLocal := {},
    publishArtifact := false
  )

  def isSbv(sbv: String) =
    Def.setting {
      val Array(maj, min) = sbv.split('.').map(_.toInt)
      CrossVersion.partialVersion(scalaBinaryVersion.value) match {
        case Some((`maj`, `min`)) => true
        case _ => false
      }
    }

  def onlyPublishIn(sbv: String) = Seq(
    publishArtifact := {
      val sbv0 = CrossVersion.partialVersion(scalaBinaryVersion.value).fold("") {
        case (maj, min) => s"$maj.$min"
      }
      sbv == sbv0 && publishArtifact.value
    },
    mimaPreviousArtifacts := {
      val previous = mimaPreviousArtifacts.value
      val publish = publishArtifact.value
      if (publish)
        previous
      else
        Set.empty
    }
  )

  def dontPublishScalaJsIn(sbv: String*) = Seq(
    publishArtifact := {
      (!ScalaJSPlugin.autoImport.isScalaJSProject.value || !sbv.contains(scalaBinaryVersion.value)) &&
        publishArtifact.value
    },
    mimaPreviousArtifacts := {
      val previous = mimaPreviousArtifacts.value
      val publish = publishArtifact.value
      if (publish)
        previous
      else
        Set.empty
    }
  )

}
