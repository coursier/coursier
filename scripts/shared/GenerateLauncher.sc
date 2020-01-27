
import $file.Util

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
  mainClass: String // FIXME Get from cp / manifest
): Unit = {

  val cpCmd = Seq(
    coursierLauncher,
    "fetch",
    "--classpath",
    module
  ) ++ extraArgs

  val cp = Util.output(cpCmd).trim

  def run(extraNativeImageOpts: String*): Unit = {

    val cmd = Seq(
      coursierLauncher,
      "launch",
      "org.graalvm.nativeimage:svm-driver:19.3.1",
      "--",
      "-cp", cp
    ) ++ extraNativeImageOpts ++ Seq(
      mainClass,
      output
    )

    System.err.println("Running " + cmd.mkString(" "))

    val mem =
      if (Util.os == "linux") "4g"
      else "3g"

    Util.run(cmd, Seq("JAVA_OPTS" -> s"-Xmx$mem"))
  }

  if (Util.os == "win") {
    // getting weird TLS-related linking errors without this
    val javaSecurityOverrides =
      """security.provider.3=what.we.put.here.doesnt.matter.ButThisHasToBeOverridden
        |""".stripMargin.getBytes
    Util.withTmpFile("java.security.overrides-", ".properties", javaSecurityOverrides) { path =>
      run(s"-J-Djava.security.properties=$path")
    }
  } else
    run()
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
