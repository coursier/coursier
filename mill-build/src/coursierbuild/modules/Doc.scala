package coursierbuild.modules

import coursierbuild.Deps.{Deps, sbtCoursierVersion}
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
    repo: String = "coursier/versioned-docs",
    branch: String = "master",
    docusaurusDir: String = "doc/website"
  ) = Task.Command {
    val dir = os.Path(docusaurusDir, BuildCtx.workspaceRoot)
    DocHelpers.copyDocusaurusVersionedData(repo, branch, dir, Task.dest / "repo")
  }

  def forkWorkingDir = Task.dest

  def generate(args: String*) = Task.Command {

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
      else """//""" + "\n"

    val outputDir = BuildCtx.workspaceRoot / "doc" / "processed-docs"

    val allArgs: Seq[String] = Seq(
      "--classpath",
      classPath().map(_.path.toString).mkString(File.pathSeparator),
      "--in",
      (BuildCtx.workspaceRoot / "doc" / "docs").toString,
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

    val websiteDir = BuildCtx.workspaceRoot / "doc" / "website"

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
        jvmArgs = Nil,
        env = forkEnv(),
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
  import java.io.File

  private def withBgProcess[T](
    cmd: Seq[String],
    dir: File = new File("."),
    waitFor: () => Unit = null
  )(f: => T): T = {

    val b = new ProcessBuilder(cmd *)
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
