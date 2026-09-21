package coursierbuild.modules

import mill.*
import mill.api.*
import mill.scalalib.*

trait CoursierPublishModule extends PublishModule
    with CoursierJavaModule {
  import mill.scalalib.publish._

  def docJar = Task {
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
  def publishVersion = Task.Input {
    CoursierPublishModule.fixedVersionOpt(Task.env)
      .getOrElse(CoursierPublishModule.computeBuildVersion())
  }
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
    // '--match v*' is needed, as git describe otherwise prefers annotated tags, and picks the
    // 'interface-v*' one when both kinds of tags sit on the same commit, like for releases
    val maybeExactTag = scala.util.Try {
      // FIXME Print stderr if command fails
      os.proc("git", "describe", "--exact-match", "--tags", "--match", "v*", gitHead)
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

  /** Environment variable making the build use a fixed, commit-independent version
    *
    * Set by `.github/scripts/selective-tests.sh`. Mill's selective execution re-runs every task
    * downstream of a `Task.Input` whose value changed since `selective.prepare` ran, and the
    * git-derived version ends up in a generated source of `core`, at the root of the module graph:
    * left as is, it would make every commit invalidate every test. The fixed version keeps the
    * snapshot-ness of the real one, so that the checks of `SnapshotOnlyPublishModule` still pass.
    */
  val fixedVersionEnvVar = "COURSIER_SELECTIVE_TESTING"

  def fixedVersionOpt(env: Map[String, String]): Option[String] =
    if (env.contains(fixedVersionEnvVar))
      Some(if (buildVersionIsSnapshot) "0.0.0-SNAPSHOT" else "0.0.0")
    else
      None

  def isSnapshot(version: String): Boolean =
    version.endsWith("-SNAPSHOT")

  lazy val buildVersionIsSnapshot = isSnapshot(buildVersion)

  lazy val millDiscover: Discover = Discover[this.type]
}
