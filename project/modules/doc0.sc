import $file.^.deps, deps.{Deps, sbtCoursierVersion}
import $file.^.relativize, relativize.{relativize => doRelativize}
import $file.shared, shared.latestTaggedVersion
import $file.^.docHelpers

import java.io.File

import mill._, mill.scalalib._

trait Doc extends ScalaModule {
  def version: T[String]
  def classPath: T[Seq[PathRef]]

  def ivyDeps = Agg(
    Deps.mdoc
  )
  def mainClass = Some("mdoc.Main")

  def copyVersionedData(
    repo: String = "coursier/versioned-docs",
    branch: String = "master",
    docusaurusDir: String = "doc/website"
  ) = T.command {
    val dir = os.Path(docusaurusDir, T.workspace)
    docHelpers.copyDocusaurusVersionedData(repo, branch, dir, T.dest / "repo")
  }

  def forkWorkingDir = T.dest

  def generate(args: String*) = T.command {

    def processArgs(
      npmInstall: Boolean,
      yarnRunBuild: Boolean,
      watch: Boolean,
      relativize: Boolean,
      args: List[String]
    ): (Boolean, Boolean, Boolean, Boolean, List[String]) =
      args match {
        case "--npm-install" :: rem    => processArgs(true, yarnRunBuild, watch, relativize, rem)
        case "--yarn-run-build" :: rem => processArgs(npmInstall, true, watch, relativize, rem)
        case "--watch" :: rem      => processArgs(npmInstall, yarnRunBuild, true, relativize, rem)
        case "--relativize" :: rem => processArgs(npmInstall, yarnRunBuild, watch, true, rem)
        case other :: rem          => sys.error(s"Unrecognized argument: $other")
        case _                     => (npmInstall, yarnRunBuild, watch, relativize, args)
      }
    val (npmInstall, yarnRunBuild, watch, relativize, args0) =
      processArgs(false, false, false, false, args.toList)

    val ver           = version()
    val latestRelease = latestTaggedVersion
    val scalaVer      = scalaVersion()

    def extraSbt(ver: String) =
      if (ver.endsWith("SNAPSHOT")) """resolvers += Resolver.sonatypeRepo("snapshots")""" + "\n"
      else ""

    val outputDir = T.workspace / "doc" / "processed-docs"

    val allArgs: Seq[String] = Seq(
      "--classpath",
      classPath().map(_.path.toString).mkString(File.pathSeparator),
      "--in",
      (T.workspace / "doc" / "docs").toString,
      "--out",
      outputDir.toString,
      "--site.VERSION",
      ver,
      "--site.EXTRA_SBT",
      extraSbt(ver),
      "--site.PLUGIN_VERSION",
      sbtCoursierVersion,
      "--site.PLUGIN_EXTRA_SBT",
      extraSbt(sbtCoursierVersion),
      "--site.SCALA_VERSION",
      scalaVer
    ) ++ (if (watch) Seq("--watch") else Nil) ++ args0

    // TODO Run yarn run thing right after, add --watch mode

    val websiteDir = T.workspace / "doc" / "website"

    if (npmInstall)
      os.proc("npm", "install").call(
        cwd = websiteDir,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )

    def runMdoc(): Unit =
      // adapted from https://github.com/com-lihaoyi/mill/blob/c500ca986ab79af3ce59ba65a093146672092307/scalalib/src/JavaModule.scala#L488-L494
      mill.util.Jvm.callProcess(
        mainClass = finalMainClass(),
        classPath = runClasspath().map(_.path),
        env = forkEnv(),
        mainArgs = allArgs,
        cwd = forkWorkingDir()
      )

    if (watch)
      if (yarnRunBuild)
        Util.withBgProcess(
          Seq("yarn", "run", "start"),
          dir = websiteDir.toIO,
          waitFor = () => Util.waitForDir(outputDir.toIO)
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
          stdin = os.Inherit,
          stdout = os.Inherit,
          stderr = os.Inherit
        )
      if (relativize)
        doRelativize((websiteDir / "build").toNIO)
    }
  }
}

private object Util {
  import java.io.File

  def withBgProcess[T](
    cmd: Seq[String],
    dir: File = new File("."),
    waitFor: () => Unit = null
  )(f: => T): T = {

    val b = new ProcessBuilder(cmd: _*)
    b.inheritIO()
    b.directory(dir)
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

  def waitForDir(dir: File): Unit = {
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
