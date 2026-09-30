package coursierbuild.modules

import mill.*
import mill.api.*
import mill.scalalib.*

import coursierbuild.SnapshotVersion

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

  /** The version after `v` if `HEAD` has a `v*` tag, else the version following the latest `v*` tag
    * (`2.1.25` -> `2.1.26-SNAPSHOT`)
    */
  private def computeBuildVersion(): String = {
    // '--match v*' is needed, as git describe otherwise prefers annotated tags, and picks the
    // 'interface-v*' one when both kinds of tags sit on the same commit, like for releases
    val res = os.proc("git", "describe", "--exact-match", "--tags", "--match", "v*", "HEAD")
      .call(cwd = BuildCtx.workspaceRoot, stderr = os.Pipe, check = false)
    if (res.exitCode == 0) res.out.trim().stripPrefix("v")
    else SnapshotVersion.next(latestTaggedVersion.stripPrefix("v"))
  }

  lazy val buildVersion = computeBuildVersion()

  def isSnapshot(version: String): Boolean =
    version.endsWith("-SNAPSHOT")

  lazy val buildVersionIsSnapshot = isSnapshot(buildVersion)

  lazy val millDiscover: Discover = Discover[this.type]
}
