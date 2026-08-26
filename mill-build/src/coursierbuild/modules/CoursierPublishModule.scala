package coursierbuild.modules

import mill.*
import mill.api.*
import mill.scalalib.*

trait CoursierPublishModule extends PublishModule
    with CoursierJavaModule {
  import mill.scalalib.publish._

  override def docJar = Task {
    CoursierPublishModule.emptyDocJar()
  }

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

object CoursierPublishModule extends ExternalModule {

  def emptyDocJar = Task {
    val dest = Task.dest / "empty.zip"
    val baos = new java.io.ByteArrayOutputStream
    val zos  = new java.util.zip.ZipOutputStream(baos)
    zos.finish()
    zos.close()
    os.write(dest, baos.toByteArray)
    PathRef(dest)
  }

  lazy val latestTaggedVersion = os.proc("git", "describe", "--abbrev=0", "--tags", "--match", "v*")
    .call().out
    .trim()
  private def computeBuildVersion() = {
    // FIXME Print stderr if command fails
    val gitHead = os.proc("git", "rev-parse", "HEAD")
      .call(cwd = BuildCtx.workspaceRoot, stderr = os.Pipe)
      .out.trim()
    val maybeExactTag = scala.util.Try {
      // FIXME Print stderr if command fails
      os.proc("git", "describe", "--exact-match", "--tags", "--always", gitHead)
        .call(cwd = BuildCtx.workspaceRoot, stderr = os.Pipe).out
        .trim()
        .stripPrefix("v")
    }
    maybeExactTag.toOption.getOrElse {
      // FIXME Print stderr if command fails
      val commitsSinceTaggedVersion =
        os.proc("git", "rev-list", gitHead, "--not", latestTaggedVersion, "--count")
          .call(cwd = BuildCtx.workspaceRoot, stderr = os.Pipe).out.trim()
          .toInt
      val gitHash = os.proc("git", "rev-parse", "--short", "HEAD")
        .call(cwd = BuildCtx.workspaceRoot)
        .out.trim()
      s"${latestTaggedVersion.stripPrefix("v")}-$commitsSinceTaggedVersion-$gitHash-SNAPSHOT"
    }
  }

  lazy val buildVersion = computeBuildVersion()

  lazy val millDiscover: Discover = Discover[this.type]
}
