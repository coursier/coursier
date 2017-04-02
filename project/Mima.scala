import com.typesafe.tools.mima.plugin.MimaKeys._
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport.mimaBinaryIssueFilters
import sbt.Keys._
import sbt._

object Mima {

  // Clear the mimaBinaryIssueFilters below on updating this fields
  val binaryCompatibilityVersion = "1.0.0-M14"
  val binaryCompatibility212Version = "1.0.0-M15"

  lazy val settings = Seq(
    mimaPreviousArtifacts := {
      val version = scalaBinaryVersion.value match {
        case "2.12" => binaryCompatibility212Version
        case _ => binaryCompatibilityVersion
      }

      Set(organization.value %% moduleName.value % version)
    },
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import ProblemFilters.exclude

      Seq(
        exclude[DirectMissingMethodProblem](
          "coursier.TermDisplay#UpdateDisplayRunnable.cleanDisplay"),
        exclude[FinalClassProblem]("coursier.TermDisplay$DownloadInfo"),
        exclude[FinalClassProblem]("coursier.TermDisplay$CheckUpdateInfo"),
        exclude[FinalClassProblem]("coursier.util.Base64$B64Scheme"),
        exclude[MissingClassProblem]("coursier.TermDisplay$Message$Stop$"),
        exclude[MissingClassProblem]("coursier.TermDisplay$Message"),
        exclude[MissingClassProblem]("coursier.TermDisplay$Message$"),
        exclude[MissingClassProblem]("coursier.TermDisplay$Message$Update$"),
        exclude[MissingClassProblem](
          "coursier.TermDisplay$UpdateDisplayThread"),
        exclude[IncompatibleMethTypeProblem]("coursier.util.Tree.apply")
      )
    }
  )

}
