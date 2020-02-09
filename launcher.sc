
import $ivy.`com.softwaremill.sttp.client::core:2.0.0-RC6`
import $ivy.`com.lihaoyi::ujson:0.9.5`

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
    if (!Files.exists(Paths.get(path)))
      Util.run(Seq("sbt", "cli/pack"))
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

private def doWaitForSync(
  initialLauncher: String,
  version: String
): Unit =
  WaitForSync(
    initialLauncher,
    s"io.get-coursier::coursier-cli:$version",
    Seq("--no-default", "-r", "central", "-r", "typesafe:ivy-releases"),
    "sonatype:public",
    attempts = 25
  )

@main
def uploadJavaLauncher(): Unit = {

  val token = if (dryRun) "" else ghToken()

  val initialLauncher0 = initialLauncher(None, None)

  val version = Version.latestFromTravisTag

  println(version)

  doWaitForSync(initialLauncher0, version)

  val javaLauncher = "./coursier"

  GenerateLauncher(
    initialLauncher0,
    module = s"io.get-coursier::coursier-cli:$version",
    extraArgs = Seq(
      "--no-default",
      "-r", "central",
      "-r", "typesafe:ivy-releases",
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
  val version = Version.latestFromTravisTag
  GenerateLauncher(
    initialLauncher0,
    module = s"io.get-coursier::coursier-cli:$version",
    extraArgs = Seq(
      "--no-default",
      "-r", "central",
      "-r", "typesafe:ivy-releases",
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
def uploadNativeImage(): Unit = {
  val token = if (dryRun) "" else ghToken()
  val initialLauncher0 = initialLauncher(None, None)
  val dest = "./cs"
  val version = Version.latestFromTravisTag
  GenerateLauncher.nativeImage(
    initialLauncher0,
    module = s"io.get-coursier::coursier-cli:$version",
    extraArgs = Seq(
      "--no-default",
      "-r", "central",
      "-r", "typesafe:ivy-releases",
    ),
    output = dest,
    mainClass = "coursier.cli.Coursier",
    // sometimes getting command-too-long errors when starting native-image
    // with the full classpath, without this
    useAssembly = Util.os == "win"
  )

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
