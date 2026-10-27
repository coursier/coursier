package coursierbuild.modules

import coursierbuild.{Deps, Versions}
import coursierbuild.Relativize.{relativize => doRelativize}
import coursierbuild.modules.CoursierPublishModule.latestTaggedVersion
import coursierbuild.DocHelpers
import java.io.File

import mill.*
import mill.api.*
import mill.scalalib.*

trait Doc extends ScalaModule {
  def version: T[String]
  def classPath: T[Seq[PathRef]]

  def mvnDeps = Seq(
    Deps.mdoc
  )
  def mainClass = Some("mdoc.Main")

  def copyVersionedData(
    repo: String = Doc.versionedDocsRepo,
    branch: String = Doc.versionedDocsBranch,
    docusaurusDir: String = "doc/website"
  ) = Task.Command {
    val dir = os.Path(docusaurusDir, BuildCtx.workspaceRoot)
    DocHelpers.copyDocusaurusVersionedData(repo, branch, dir, Task.dest / "repo")
  }

  def forkWorkingDir = Task.dest

  // not under moduleDir (that's modules/doc)
  private def websiteDir = BuildCtx.workspaceRoot / "doc/website"

  /** The pages processed by mdoc in [[generate]] */
  def mdocInput = Task.Source(BuildCtx.workspaceRoot / "doc/docs")

  /** What npm and Docusaurus read under doc/website in [[generate]]
    *
    * Not the whole directory, as npm and Docusaurus write in it (see doc/website/.gitignore). Keep
    * this in sync with the files under doc/website, for selective execution to consider the website
    * affected when they change.
    */
  def websiteSources = Task.Sources(
    websiteDir / "blog",
    websiteDir / "core",
    websiteDir / "pages",
    websiteDir / "static",
    websiteDir / "package.json",
    websiteDir / "package-lock.json",
    websiteDir / "sidebars.json",
    websiteDir / "siteConfig.js"
  )

  def generate(args: String*) = Task.Command {

    def processArgs(
      copyVersionedData: Boolean,
      npmInstall: Boolean,
      yarnRunBuild: Boolean,
      watch: Boolean,
      relativize: Boolean,
      args: List[String]
    ): (Boolean, Boolean, Boolean, Boolean, Boolean, List[String]) =
      args match {
        case "--copy-versioned-data" :: rem =>
          processArgs(true, npmInstall, yarnRunBuild, watch, relativize, rem)
        case "--npm-install" :: rem =>
          processArgs(copyVersionedData, true, yarnRunBuild, watch, relativize, rem)
        case "--yarn-run-build" :: rem =>
          processArgs(copyVersionedData, npmInstall, true, watch, relativize, rem)
        case "--watch" :: rem =>
          processArgs(copyVersionedData, npmInstall, yarnRunBuild, true, relativize, rem)
        case "--relativize" :: rem =>
          processArgs(copyVersionedData, npmInstall, yarnRunBuild, watch, true, rem)
        case other :: rem => sys.error(s"Unrecognized argument: $other")
        case _            => (copyVersionedData, npmInstall, yarnRunBuild, watch, relativize, args)
      }
    val (copyVersionedData0, npmInstall, yarnRunBuild, watch, relativize, args0) =
      processArgs(false, false, false, false, false, args.toList)

    val ver           = version()
    val latestRelease = latestTaggedVersion
    val scalaVer      = scalaVersion()

    def extraSbt(ver: String) =
      if (ver.endsWith("SNAPSHOT")) """resolvers += Resolver.sonatypeRepo("snapshots")""" + "\n"
      else """//""" + "\n"

    val outputDir = BuildCtx.workspaceRoot / "doc" / "processed-docs"

    val allArgs: Seq[String] = Seq(
      "--classpath",
      classPath().map(_.path.toString).mkString(File.pathSeparator),
      "--in",
      mdocInput().path.toString,
      "--out",
      outputDir.toString,
      "--site.VERSION",
      ver,
      "--site.EXTRA_SBT",
      extraSbt(ver),
      "--site.PLUGIN_VERSION",
      Versions.sbtCoursier,
      "--site.PLUGIN_EXTRA_SBT",
      extraSbt(Versions.sbtCoursier),
      "--site.SCALA_VERSION",
      scalaVer
    ) ++ (if (watch) Seq("--watch") else Nil) ++ args0

    // TODO Run yarn run thing right after, add --watch mode

    // npm and Docusaurus read these from websiteDir, this only makes Mill track them
    val _ = websiteSources()

    // Done here rather than via copyVersionedData, as Mill doesn't order the tasks a command
    // depends on, so that the versioned docs are there before Docusaurus builds the site
    if (copyVersionedData0)
      DocHelpers.copyDocusaurusVersionedData(
        Doc.versionedDocsRepo,
        Doc.versionedDocsBranch,
        websiteDir,
        Task.dest / "versioned-docs"
      )

    // --ignore-scripts: the install scripts of some dependencies (the imagemin *-bin
    // packages notably) download and run binaries with no integrity check, and
    // nothing we use needs them (image compression is disabled in package.json)
    if (npmInstall)
      os.proc("npm", "ci", "--ignore-scripts").call(
        cwd = websiteDir,
        env = DocHelpers.withoutSecretsEnv,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )

    def runMdoc(): Unit =
      // adapted from https://github.com/com-lihaoyi/mill/blob/c500ca986ab79af3ce59ba65a093146672092307/scalalib/src/JavaModule.scala#L488-L494
      mill.util.Jvm.callProcess(
        mainClass = finalMainClass(),
        classPath = runClasspath().map(_.path),
        jvmArgs = Nil,
        // mdoc runs code from the pages and from their dependencies, keep secrets out of its env
        env = DocHelpers.withoutSecrets(Task.env) ++ forkEnv(),
        propagateEnv = false,
        mainArgs = allArgs,
        cwd = forkWorkingDir(),
        stdout = os.Inherit
      )

    if (watch)
      if (yarnRunBuild)
        Doc.withBgProcess(
          Seq("yarn", "run", "start"),
          dir = websiteDir.toIO,
          waitFor = () => Doc.waitForDir(outputDir.toIO)
        ) {
          runMdoc()
        }
      else
        runMdoc()
    else {
      runMdoc()
      if (yarnRunBuild)
        os.proc("yarn", "run", "build").call(
          cwd = websiteDir,
          env = DocHelpers.withoutSecretsEnv,
          stdin = os.Inherit,
          stdout = os.Inherit,
          stderr = os.Inherit
        )
      if (relativize)
        doRelativize((websiteDir / "build").toNIO)
    }
  }
}

object Doc {

  val versionedDocsRepo   = "coursier/versioned-docs"
  val versionedDocsBranch = "master"

  import java.io.File

  private def withBgProcess[T](
    cmd: Seq[String],
    dir: File = new File("."),
    waitFor: () => Unit = null
  )(f: => T): T = {

    val b = new ProcessBuilder(cmd *)
    b.inheritIO()
    b.directory(dir)
    for (name <- DocHelpers.secretEnvVars)
      b.environment().remove(name)
    var p: Process = null

    Option(waitFor) match {
      case Some(w) =>
        val t = new Thread("wait-for-condition") {
          setDaemon(true)
          override def run() = {
            w()
            System.err.println(s"Running ${cmd.mkString(" ")}")
            p = b.start()
          }
        }
        t.start()
      case None =>
        System.err.println(s"Running ${cmd.mkString(" ")}")
        p = b.start()
    }

    try f
    finally {
      p.destroy()
      p.waitFor(1L, java.util.concurrent.TimeUnit.SECONDS)
      p.destroyForcibly()
    }
  }

  private def waitForDir(dir: File): Unit = {
    @annotation.tailrec
    def helper(): Unit = {
      val found =
        dir.exists() && {
          assert(dir.isDirectory)
          dir.listFiles().nonEmpty
        }

      if (!found) {
        Thread.sleep(200L)
        helper()
      }
    }

    helper()
  }
}
