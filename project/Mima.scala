
import sbt._
import sbt.Keys._

import com.typesafe.tools.mima.plugin.MimaKeys._

object Mima {

  def binaryCompatibilityVersion = "1.0.0-RC1"


  lazy val previousArtifacts = Seq(
    mimaPreviousArtifacts := {
      Set(organization.value %% moduleName.value % binaryCompatibilityVersion)
    }
  )

  lazy val coreFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq()
    }
  }

  lazy val cacheFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq()
    }
  }

}
