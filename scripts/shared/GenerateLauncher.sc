
import $file.Util

import java.nio.file.Files

/**
 * Generates a native-image for module `module`.
 *
 * @param coursierLauncher Path to a coursier launcher to fetch `module` classpath and native-image
 * @param module A module to fetch, like `"org:name:version"`
 * @param extraArgs Extra arguments to pass to coursier to fetch `module`, e.g. `Seq("-r", "sonatype:snapshots")`
 * @param output Path to the native image to generate
 * @param mainClass Main class that the native image should start
 */
def nativeImage(
  coursierLauncher: String,
  module: String,
  extraArgs: Seq[String],
  output: String,
  mainClass: String, // FIXME Get from cp / manifest
  useAssembly: Boolean = false,
  extraNativeImageOpts: Seq[String] = Nil
): Unit = {

  val cp =
    if (useAssembly) {

      val tempFile = Files.createTempFile("assembly-", ".jar")

      Runtime.getRuntime().addShutdownHook(
        new Thread {
          override def run(): Unit =
            Files.deleteIfExists(tempFile)
        }
      )

      val assemblyPath = tempFile.toAbsolutePath.toString

      val assemblyCmd = Seq(
        coursierLauncher,
        "bootstrap",
        "--assembly",
        "--preamble=false",
        "-o", assemblyPath,
        "-f",
        module
      ) ++ extraArgs

      Util.run(assemblyCmd)
      assemblyPath
    } else {
      val cpCmd = Seq(
        coursierLauncher,
        "fetch",
        "--classpath",
        module
      ) ++ extraArgs

      Util.output(cpCmd).trim
    }

  val mem =
    if (Util.os == "linux") "3584m"
    else "3g"

  val graalvmVer = if (Util.os == "win") "20.0.0" else "20.1.0"

  def run(extraNativeImageOpts: Seq[String], extraCsLaunchOpts: Seq[String] = Nil): Unit = {

    val cmd = Seq(
      coursierLauncher,
      "launch"
    ) ++ extraCsLaunchOpts ++ Seq(
      // "--jvm", "graalvm:19.3",
      // "--java-opt", s"-Xmx$memm",
      // "--fork",
      s"org.graalvm.nativeimage:svm-driver:$graalvmVer",
      "--",
      "-cp", cp
    ) ++ extraNativeImageOpts ++ Seq(
      mainClass,
      output
    )

    System.err.println("Running " + cmd.mkString(" "))

    Util.run(cmd, Seq("JAVA_OPTS" -> s"-Xmx$mem"))
  }

  if (Util.os == "win") {
    val extraCsOpts = if (coursierLauncher.endsWith("cs") || coursierLauncher.endsWith("cs.bat") || coursierLauncher.endsWith("cs.exe")) Seq("--java-opt", s"-Xmx$mem") else Nil
    // getting weird TLS-related linking errors without this
    val javaSecurityOverrides =
      """security.provider.3=what.we.put.here.doesnt.matter.ButThisHasToBeOverridden
        |""".stripMargin.getBytes
    Util.withTmpFile("java.security.overrides-", ".properties", javaSecurityOverrides) { path =>
      run(s"-J-Djava.security.properties=$path" +: extraNativeImageOpts, extraCsOpts)
    }
  } else if (Util.os == "linux" && coursierLauncher.endsWith("cs"))
    run(extraNativeImageOpts, Seq("--java-opt", s"-Xmx$mem"))
  else
    run(extraNativeImageOpts)
}

/**
 * Generates a launcher (using the bootstrap command of coursier).
 *
 * @param coursierLauncher Path to a coursier launcher
 * @param module Module to be launched, like `"org:name:version"`
 * @param extraArgs Extra arguments to pass to coursier to fetch `module`, e.g. `Seq("-r", "sonatype:snapshots")`
 * @param output Path to the launcher to generate
 * @param forceBat Whether to force generating a `.bat` file along with the launcher
 *
 */
def apply(
  coursierLauncher: String,
  module: String,
  extraArgs: Seq[String],
  output: String,
  forceBat: Boolean = false
): Unit = {

  var cmd = Seq(coursierLauncher, "bootstrap", module) ++
    extraArgs ++
    Seq("-f", "-o", output)

  if (forceBat)
    cmd = cmd ++ Seq("--bat=true")

  Util.run(cmd)
}
