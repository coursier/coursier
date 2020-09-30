
// Ammonite 2.1.4-11-307f3d8, scala 2.12.12

import $file.scripts.shared.GenerateLauncher
import $file.scripts.shared.Sign
import $file.scripts.shared.UploadGhRelease
import $file.scripts.shared.UploadRepo
import $file.scripts.shared.Util
import $file.scripts.shared.Version
import $file.scripts.shared.WaitForSync


private def ghToken() = Option(System.getenv("GH_TOKEN")).getOrElse {
  sys.error("GH_TOKEN not set")
}


private def initialLauncher(
  initialLauncher: Option[String],
  buildPackLauncher: Option[Boolean]
): String = {

  import java.nio.file.{Files, Paths}

  def packLauncher: String = {
    val path = "modules/cli/target/pack/bin/coursier"
    val sbtPath =
      if (Util.os == "win") "bin/sbt"
      else "./sbt"
    if (!Files.exists(Paths.get(path)))
      Util.run(Seq(sbtPath, "cli/pack"))
    path
  }

  (initialLauncher, buildPackLauncher) match {
    case (None, None) =>
      packLauncher
    case (Some(path), None) =>
      path
    case (Some(path), Some(false)) =>
      path
    case (Some(path), Some(true)) =>
      sys.error("Cannot pass both --initial-launcher and --build-pack-launcher")
    case (None, Some(true)) =>
      packLauncher
    case (None, Some(false)) =>
      sys.error("--build-pack-launcher=false requires an initial launcher to be passed via --initial-launcher")
  }
}

private val pgpPassphrase = Option(System.getenv("PGP_PASSPHRASE"))
private val pgpSecret = Option(System.getenv("PGP_SECRET"))

private val dryRun = false

@main
def uploadJavaLauncher(): Unit = {

  val token = if (dryRun) "" else ghToken()

  val initialLauncher0 = initialLauncher(None, None)

  val version = Version.latestFromEnv

  println(version)

  val module = s"io.get-coursier::coursier-cli:$version"

  WaitForSync(
    initialLauncher0,
    module,
    Seq("--no-default", "-r", "central"),
    "sonatype:public",
    attempts = 25
  )

  val javaLauncher = "./coursier"

  GenerateLauncher(
    initialLauncher0,
    module = module,
    extraArgs = Seq(
      "--no-default",
      "-r", "central",
    ),
    output = javaLauncher,
    forceBat = true
  )

  val generatedFiles = Sign(
    Seq(
      javaLauncher -> "coursier",
      s"$javaLauncher.bat" -> "coursier.bat"
    ),
    pgpPassphrase = pgpPassphrase,
    pgpSecret = pgpSecret
  )

  UploadGhRelease(
    generatedFiles,
    "coursier",
    "coursier",
    ghToken = token,
    version = version,
    dryRun = dryRun
  )

  UploadRepo(
    generatedFiles,
    "coursier",
    "coursier",
    "gh-pages",
    ghToken = token,
    s"Add $version launcher",
    dryRun = dryRun
  )

  UploadRepo(
    generatedFiles,
    "coursier",
    "launchers",
    "master",
    ghToken = token,
    s"Add $version launcher",
    dryRun = dryRun
  )
}

@main
def uploadAssembly(): Unit = {
  val token = if (dryRun) "" else ghToken()
  val initialLauncher0 = initialLauncher(None, None)
  val assembly = "./coursier.jar"
  val version = Version.latestFromEnv
  GenerateLauncher(
    initialLauncher0,
    module = s"io.get-coursier::coursier-cli:$version",
    extraArgs = Seq(
      "--no-default",
      "-r", "central",
      "--assembly"
    ),
    output = assembly
  )

  val generatedFiles = Sign(
    Seq(
      assembly -> "coursier.jar"
    ),
    pgpPassphrase = pgpPassphrase,
    pgpSecret = pgpSecret
  )

  UploadGhRelease(
    generatedFiles,
    "coursier",
    "coursier",
    ghToken = token,
    version = version,
    dryRun = dryRun
  )

  UploadRepo(
    generatedFiles,
    "coursier",
    "launchers",
    "master",
    ghToken = token,
    s"Add $version assembly",
    dryRun = dryRun
  )
}

@main
def signDummyFiles(): Unit =
  Sign(
    Seq(
      "build.sbt" -> "build.sbt"
    ),
    pgpPassphrase = pgpPassphrase,
    pgpSecret = pgpSecret
  )

@main
def generateNativeImage(
  version: String = Version.latestFromTag,
  output: String = "./cs",
  allowIvy2Local: Boolean = true
): Unit = {
  val initialLauncher0 = sys.env.getOrElse("CS", initialLauncher(None, None))
  GenerateLauncher.nativeImage(
    initialLauncher0,
    module = s"io.get-coursier::coursier-cli:$version",
    extraArgs =
      (if (allowIvy2Local) Nil else Seq("--no-default")) ++
      Seq(
        "-r", "central",
        "org.scalameta::svm-subs:20.1.0"
      ),
    output = output,
    mainClass = "coursier.cli.Coursier",
    // sometimes getting command-too-long errors when starting native-image
    // with the full classpath, without this
    useAssembly = Util.os == "win",
    extraNativeImageOpts = Seq(
      // remove upon next release
      "--initialize-at-build-time=scala.meta.internal.svm_subs.UnsafeUtils",
      "--initialize-at-build-time=scala.collection.immutable.VM",
      "--report-unsupported-elements-at-runtime"
      // "-H:-CheckToolchain" // sometimes needed on Windows, see https://github.com/oracle/graal/issues/2522
    )
  )
}

@main
def uploadNativeImage(): Unit = {
  val token = if (dryRun) "" else ghToken()
  val dest = "./cs"
  val version = Version.latestFromEnv
  generateNativeImage(version, dest, allowIvy2Local = false)

  // TODO Check that we are on the right CPU too?
  val platformSuffix = Util.os match {
    case "linux" => "x86_64-pc-linux"
    case "mac" => "x86_64-apple-darwin"
    case "win" => "x86_64-pc-win32"
    case other => ???
  }

  val extension = Util.os match {
    case "win" => ".exe"
    case _ => ""
  }

  val actualDest = dest + extension

  val generatedFiles = Sign(
    Seq(
      actualDest -> s"cs-$platformSuffix$extension"
    ),
    pgpPassphrase = pgpPassphrase,
    pgpSecret = pgpSecret
  )

  UploadGhRelease(
    generatedFiles,
    "coursier",
    "coursier",
    ghToken = token,
    version = version,
    dryRun = dryRun
  )

  UploadRepo(
    generatedFiles,
    "coursier",
    "launchers",
    "master",
    ghToken = token,
    s"Add $version native-image launcher for $platformSuffix",
    dryRun = dryRun
  )
}

@main
def uploadAllJars(): Unit = {
  uploadJavaLauncher()
  uploadAssembly()
}

@main
def dummy() = {}
