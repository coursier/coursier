package coursier.cli.spark

import java.io.File

import coursier.cli.deprecated.{CommonOptions, Helper}
import coursier.core.Type

object Submit {

  def cp(
    scalaVersion: String,
    sparkVersion: String,
    noDefault: Boolean,
    extraDependencies: Seq[String],
    artifactTypes: Set[Type],
    common: CommonOptions
  ): Seq[File] = {

    var extraCp = Seq.empty[File]

    for (yarnConf <- sys.env.get("YARN_CONF_DIR") if yarnConf.nonEmpty) {
      val f = new File(yarnConf)

      if (!f.isDirectory) {
        Console.err.println(s"Error: YARN conf path ($yarnConf) is not a directory or doesn't exist.")
        sys.exit(1)
      }

      extraCp = extraCp :+ f
    }

    def defaultDependencies = Seq(
      // FIXME We whould be able to pass these as (parsed) Dependency instances to Helper
      s"org.apache.spark::spark-core:$sparkVersion",
      s"org.apache.spark::spark-yarn:$sparkVersion"
    )

    val helper = new Helper(
      common.copy(
        dependencyOptions = common.dependencyOptions.copy(
          intransitive = Nil
        ),
        resolutionOptions = common.resolutionOptions.copy(
          scalaVersion = Some(scalaVersion)
        )
      ),
      // FIXME We whould be able to pass these as (parsed) Dependency instances to Helper
      (if (noDefault) Nil else defaultDependencies) ++ extraDependencies
    )

    helper.fetch(
      sources = false,
      javadoc = false,
      default = true,
      artifactTypes = artifactTypes,
      classifier0 = Set.empty
    ) ++ extraCp
  }

  def mainClassName = "org.apache.spark.deploy.SparkSubmit"

}
