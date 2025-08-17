package coursierbuild.modules

import mill._, mill.scalalib._

trait CoursierPublishModule extends PublishModule with PublishLocalNoFluff
    with CoursierJavaModule {
  import mill.scalalib.publish._
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "io.get-coursier",
    url = "https://github.com/coursier/coursier",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("coursier", "coursier"),
    developers = Seq(
      Developer("alexarchambault", "Alex Archambault", "https://github.com/alexarchambault")
    )
  )
  def publishVersion = Task.Input(CoursierPublishModule.computeBuildVersion())
}

object CoursierPublishModule {

  lazy val latestTaggedVersion = os.proc("git", "describe", "--abbrev=0", "--tags", "--match", "v*")
    .call().out
    .trim()
  private def computeBuildVersion() = {
    val gitHead = os.proc("git", "rev-parse", "HEAD").call().out.trim()
    val maybeExactTag = scala.util.Try {
      os.proc("git", "describe", "--exact-match", "--tags", "--always", gitHead)
        .call().out
        .trim()
        .stripPrefix("v")
    }
    maybeExactTag.toOption.getOrElse {
      val commitsSinceTaggedVersion =
        os.proc("git", "rev-list", gitHead, "--not", latestTaggedVersion, "--count")
          .call().out.trim()
          .toInt
      val gitHash = os.proc("git", "rev-parse", "--short", "HEAD").call().out.trim()
      s"${latestTaggedVersion.stripPrefix("v")}-$commitsSinceTaggedVersion-$gitHash-SNAPSHOT"
    }
  }

  lazy val buildVersion = computeBuildVersion()
}
