package coursierbuild.modules

import coursierbuild.{Deps, Versions}
import coursierbuild.Relativize.{relativize => doRelativize}
import coursierbuild.DocHelpers
import java.io.File

import scala.jdk.OptionConverters.*

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
  private def websiteDir    = BuildCtx.workspaceRoot / "doc/website"
  private def mdocOutputDir = BuildCtx.workspaceRoot / "doc" / "processed-docs"

  /** The pages processed by mdoc in [[generate]] and [[mdoc]] */
  def mdocInput = Task.Source(BuildCtx.workspaceRoot / "doc/docs")

  /** What npm and Docusaurus read under doc/website in [[generate]]
    *
    * Not the whole directory, as npm and Docusaurus write in it (see doc/website/.gitignore). Keep
    * this in sync with the files under doc/website, for selective execution to consider the website
    * affected when they change.
    */
  def websiteSources = Task.Sources(
    websiteDir / "blog",
    websiteDir / "src",
    websiteDir / "static",
    websiteDir / "docusaurus.config.js",
    websiteDir / "package.json",
    websiteDir / "package-lock.json",
    websiteDir / "sidebars.json"
  )

  /** The arguments passed to mdoc to process the docs sources */
  private def mdocArgs = Task.Anon {
    val ver      = version()
    val scalaVer = scalaVersion()

    def extraSbt(ver: String) =
      if (ver.endsWith("SNAPSHOT")) """resolvers += Resolver.sonatypeRepo("snapshots")""" + "\n"
      else """//""" + "\n"

    Seq(
      "--classpath",
      classPath().map(_.path.toString).mkString(File.pathSeparator),
      "--in",
      mdocInput().path.toString,
      "--out",
      mdocOutputDir.toString,
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
    )
  }

  /** Runs mdoc with the passed arguments, sending its output to `stdout` */
  private def mdocRunner = Task.Anon {
    val mainClass0 = finalMainClass()
    val classPath0 = runClasspath().map(_.path)
    // mdoc runs code from the pages and from their dependencies, keep secrets out of its env
    val env = DocHelpers.withoutSecrets(Task.env) ++ forkEnv()
    val cwd = forkWorkingDir()
    (args: Seq[String], stdout: os.ProcessOutput) =>
      // adapted from https://github.com/com-lihaoyi/mill/blob/c500ca986ab79af3ce59ba65a093146672092307/scalalib/src/JavaModule.scala#L488-L494
      mill.util.Jvm.callProcess(
        mainClass = mainClass0,
        classPath = classPath0,
        jvmArgs = Nil,
        env = env,
        propagateEnv = false,
        mainArgs = args,
        cwd = cwd,
        stdout = stdout
      )
      ()
  }

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

    val allArgs = mdocArgs() ++ (if (watch) Seq("--watch") else Nil) ++ args0

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

    // --ignore-scripts: install scripts of dependencies can download and run arbitrary
    // code, and none of the website dependencies need theirs (as of Docusaurus 3, only
    // core-js, printing a banner, and fsevents, macOS-only and shipping a prebuilt binary,
    // have some)
    if (npmInstall)
      os.proc("npm", "ci", "--ignore-scripts").call(
        cwd = websiteDir,
        env = DocHelpers.withoutSecretsEnv,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )

    val runMdoc0 = mdocRunner()
    def runMdoc(): Unit =
      runMdoc0(allArgs, os.Inherit)

    if (watch)
      if (yarnRunBuild)
        Doc.withBgProcess(
          Seq("yarn", "run", "start"),
          dir = websiteDir.toIO,
          waitFor = () => Doc.waitForDir(mdocOutputDir.toIO)
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

  /** Processes the docs sources with mdoc, writing the result in `doc/processed-docs`, where
    * Docusaurus reads it from. Being a cached task, `./mill -w` re-runs it only when the sources
    * (or the coursier modules they rely on) change.
    */
  def mdoc = Task {
    mdocInput()
    val runMdoc = mdocRunner()
    // mdoc writes its output in the workspace, rather than in Task.dest, for Docusaurus to read it
    BuildCtx.withFilesystemCheckerDisabled {
      // Everything goes to stderr, so that `./mill show` only prints the result on stdout
      runMdoc(mdocArgs(), Doc.toStderr)
      PathRef(mdocOutputDir)
    }
  }

  /** Installs the website dependencies, if they aren't already installed from the current
    * `package-lock.json`
    */
  private def npmInstall(): Unit = {
    // written by npm ci
    val installedLock = websiteDir / "node_modules" / ".package-lock.json"
    val upToDate = os.exists(installedLock) &&
      os.mtime(installedLock) >= os.mtime(websiteDir / "package-lock.json")
    if (!upToDate)
      // --ignore-scripts: see generate
      os.proc("npm", "ci", "--ignore-scripts").call(
        cwd = websiteDir,
        env = DocHelpers.withoutSecretsEnv,
        stdout = Doc.toStderr,
        stderr = os.Inherit
      )
  }

  /** Builds the website from sources, and prints the command serving it, as a shell command line:
    * use `./mill show` to get it as a JSON string, that can be passed to `eval`.
    *
    * Accepts `--host=…` (the address to bind to, `localhost` by default), `--port=…` (3000 by
    * default), and `--base-address=…`, the external address the website is accessed from, like
    * `https://host/some/path/`, when it's served behind a proxy. As Docusaurus needs it at build
    * time, the website is built for it.
    *
    * Past versions are included if they were copied to `doc/website` beforehand (see
    * [[copyVersionedData]]).
    */
  def serveCmd(args: String*) = Task.Command {
    val (host, port, env) = Doc.websiteServerOptions(args)
    mdoc()
    npmInstall()
    os.proc("npm", "run", "build").call(
      cwd = websiteDir,
      env = DocHelpers.withoutSecretsEnv ++ env,
      stdout = Doc.toStderr,
      stderr = os.Inherit
    )

    Doc.shellCommand(
      websiteDir,
      env,
      Seq(
        "npx",
        "docusaurus",
        "serve",
        "--dir",
        "build/coursier",
        "--host",
        host,
        "--port",
        port,
        "--no-open"
      )
    )
  }

  /** Where [[devServer]] keeps track of the server it runs in the background */
  def devServerBackgroundDir = Task(persistent = true) {
    Task.dest
  }

  /** Runs the Docusaurus development server in the background, the same way `runBackground` works,
    * on the docs processed by [[mdoc]]. Accepts the same options as [[serveCmd]].
    *
    * Use with `./mill -w doc.devServer …`: Mill then re-runs [[mdoc]] when the docs sources change,
    * and the server hot-reloads the pages it re-generates. The server keeps running when
    * `devServer` is run again with the same options, and is restarted otherwise. Stop it with
    * `doc.devServerStop`.
    */
  def devServer(args: String*) = Task.Command {
    val (host, port, env) = Doc.websiteServerOptions(args)
    mdoc()
    npmInstall()

    // Run the Docusaurus script with node directly, rather than via npx or npm, so that the
    // background wrapper stops the server itself when it exits
    val command = Seq(
      "node",
      "node_modules/@docusaurus/core/bin/docusaurus.mjs",
      "start",
      "--host",
      host,
      "--port",
      port,
      "--no-open"
    )
    val dir        = devServerBackgroundDir()
    val configFile = dir / "config"
    val config     = (command ++ env.toSeq.sorted.map { case (k, v) => s"$k=$v" }).mkString("\n")

    if (Doc.runningProcess(dir).nonEmpty && os.exists(configFile) && os.read(configFile) == config)
      System.err.println("The Docusaurus development server is already running")
    else {
      os.write.over(configFile, config)
      // Same as RunModule#runBackgroundTask, with the wrapper running a subprocess rather than a
      // main class. The wrapper stops the previous server, if any, before starting the new one.
      val wrapperArgs =
        Seq("newest-pid", "currently-running-pid", "lock", "log").map(n => (dir / n).toString)
      runner().run(
        args = wrapperArgs ++ Seq("<subprocess>") ++ command,
        mainClass = "mill.javalib.backgroundwrapper.MillBackgroundWrapper",
        forkEnv = DocHelpers.withoutSecrets(Task.env) ++ forkEnv() ++ env,
        workingDir = websiteDir,
        extraRunClasspath = mill.javalib.JvmWorkerModule.backgroundWrapperClasspath().map(_.path),
        background = true,
        runBackgroundLogToConsole = true,
        propagateEnv = false
      )
    }
  }

  /** Stops the server started by [[devServer]], if it's running */
  def devServerStop() = Task.Command {
    if (Doc.stopBackground(devServerBackgroundDir()))
      System.err.println("Stopped the Docusaurus development server")
    else
      System.err.println("No Docusaurus development server running")
  }
}

object Doc {

  val versionedDocsRepo   = "coursier/versioned-docs"
  val versionedDocsBranch = "master"

  import java.io.File

  private def toStderr = os.ProcessOutput.Readlines(System.err.println(_))

  /** Parses the `--host=…`, `--port=…`, and `--base-address=…` options of the website server
    * commands, and returns the host and port to bind to, and the environment variables passing the
    * external address to Docusaurus
    */
  private def websiteServerOptions(args: Seq[String]): (String, String, Map[String, String]) = {
    val optionNames = Set("--host", "--port", "--base-address")
    def parse(args: List[String], acc: Map[String, String]): Map[String, String] =
      args match {
        case Nil                                     => acc
        case opt :: value :: rem if optionNames(opt) => parse(rem, acc + (opt -> value))
        case arg :: rem if optionNames(arg.takeWhile(_ != '=')) && arg.contains('=') =>
          val (opt, value) = arg.span(_ != '=')
          parse(rem, acc + (opt -> value.drop(1)))
        case arg :: _ =>
          val expected = optionNames.toSeq.sorted.mkString(", ")
          sys.error(s"Unrecognized argument: $arg (expected $expected)")
      }
    val options = parse(args.toList, Map.empty)
    val host    = options.getOrElse("--host", "localhost")
    val port    = options.getOrElse("--port", "3000")
    val env     = options.get("--base-address").map("COURSIER_WEBSITE_URL" -> _).toMap
    (host, port, env)
  }

  /** A command line running `command` from `cwd`, with the `env` variables added to the
    * environment, to pass to `eval` in a POSIX shell (bash, zsh, …)
    *
    * Paths are made absolute: from a Mill task, `os.Path#toString` gives paths under the workspace
    * relative to the task sandbox (`../mill-workspace/…`), which don't resolve from the shell the
    * command is run in.
    */
  private def shellCommand(cwd: os.Path, env: Map[String, String], command: Seq[String]): String = {
    def isSafeShellChar(c: Char): Boolean =
      (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
      "_@%+=:,./-".contains(c)
    def shellQuote(s: String): String =
      if (s.nonEmpty && s.forall(isSafeShellChar)) s
      else "'" + s.replace("'", "'\\''") + "'"
    val words =
      Seq(s"cd ${shellQuote(cwd.toNIO.toAbsolutePath.toString)} &&") ++
        env.toSeq.map { case (k, v) => s"$k=${shellQuote(v)}" } ++
        command.map(shellQuote)
    words.mkString(" ")
  }

  // The development server runs in the background, the same way `runBackground` works in Mill:
  // it is started via Mill's `MillBackgroundWrapper` (in "subprocess" mode), which keeps track of
  // the running process in the files below. The wrapper kills the server and exits as soon as
  // the "newest PID" file doesn't contain its own PID anymore, which happens when a new server
  // takes over, when `devServerStop` is called, or when the directory gets cleaned.
  private def newestPidFile(backgroundDir: os.Path)  = backgroundDir / "newest-pid"
  private def currentPidFile(backgroundDir: os.Path) = backgroundDir / "currently-running-pid"

  private def runningProcess(backgroundDir: os.Path): Option[ProcessHandle] =
    Option(currentPidFile(backgroundDir))
      .filter(os.exists(_))
      .flatMap(f => os.read(f).trim.toLongOption)
      .flatMap(pid => ProcessHandle.of(pid).toScala)
      .filter(_.isAlive)

  /** Stops the server started in the background from `backgroundDir`, if any
    *
    * @return
    *   whether a server was running
    */
  private def stopBackground(backgroundDir: os.Path): Boolean =
    runningProcess(backgroundDir) match {
      case None => false
      case Some(wrapper) =>
        os.write.over(newestPidFile(backgroundDir), "stopped", createFolders = true)
        val deadline = System.currentTimeMillis() + 10000L
        while (wrapper.isAlive && System.currentTimeMillis() < deadline)
          Thread.sleep(100L)
        if (wrapper.isAlive) {
          wrapper.descendants().forEach(_.destroyForcibly())
          wrapper.destroyForcibly()
        }
        true
    }

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
