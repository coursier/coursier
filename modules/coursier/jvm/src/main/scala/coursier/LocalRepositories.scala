package coursier

import java.io.File

import coursier.ivy.IvyRepository

// not sure this should live in the coursier.cache namespace…

object LocalRepositories {

  private lazy val ivy2HomeUri = {

    val path =
      sys.props.get("coursier.ivy.home")
        .orElse(sys.props.get("ivy.home"))
        .getOrElse(sys.props("user.home") + "/.ivy2/")

    // a bit touchy on Windows... - don't try to manually write down the URI with s"file://..."
    val str = new File(path).toURI.toString
    if (str.endsWith("/"))
      str
    else
      str + "/"
  }

  lazy val ivy2Local = IvyRepository.fromPattern(
    (ivy2HomeUri + "local/") +: coursier.ivy.Pattern.default,
    dropInfoAttributes = true
  )

  /**
    * These repositories aren't guaranteed to always work fine with coursier (they sometimes have only the
    * metadata of some dependencies, and coursier isn't fine with that - coursier requires
    * both the metadata and the JARs to be in the same repo)
    * see https://github.com/coursier/coursier/pull/868#issuecomment-398779799
    */
  object Dangerous {

    lazy val maven2Local = {

      // TODO Add a small unit test for that repo…

      // a bit touchy on Windows... - don't try to manually write down the URI with s"file://..."
      val str = new File(sys.props("user.home")).toURI.toString
      val homeUri =
        if (str.endsWith("/"))
          str
        else
          str + "/"

      MavenRepository(homeUri + ".m2/repository")
    }

    lazy val ivy2Cache = IvyRepository.parse(
      ivy2HomeUri + "cache/" +
        "(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]s/[artifact]-[revision](-[classifier]).[ext]",
      metadataPatternOpt = Some(
        ivy2HomeUri + "cache/" +
          "(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]-[revision](-[classifier]).[ext]"
      ),
      withChecksums = false,
      withSignatures = false,
      dropInfoAttributes = true
    ).right.getOrElse(
      throw new Exception("Cannot happen")
    )
  }

}
