package coursier.clitests

import utest._

import scala.concurrent.duration.DurationInt
import scala.util.Properties

abstract class SetupTests extends TestSuite {

  // Fail eagerly rather than letting a stuck process hang until the whole CI
  // job times out (and gets force-killed, leaving orphan processes behind).
  private val setupTimeout = 10.minutes.toMillis

  def launcher: String
  def assembly: String
  def isNative: Boolean
  def isNativeStatic: Boolean

  def hasDocker: Boolean =
    Properties.isLinux

  def alpineJavaImage =
    sys.props.getOrElse(
      "coursier.test.alpine-java-image",
      sys.error("coursier.test.alpine-java-image Java property not set")
    )
  def alpineImage =
    sys.props.getOrElse(
      "coursier.test.alpine-image",
      sys.error("coursier.test.alpine-image Java property not set")
    )

  def tests = Tests {

    test("setup") {
      TestUtil.withTempDir { tempDir =>
        val homeDir    = os.Path(tempDir, os.pwd)
        val installDir = homeDir / "bin"
        // TODO Temporary diagnostics: run verbosely and stream the command's output live
        // (rather than capturing it), with timestamps around the call, so that if `cs setup`
        // stalls again we can tell from the CI logs where it got stuck. Remove once the
        // standalone setup hang is understood.
        System.err.println(s"[${java.time.Instant.now()}] Running cs setup")
        val result = os.proc(
          launcher,
          "setup",
          "--yes",
          "-v",
          "-v",
          "--user-home",
          homeDir.toString,
          "--install-dir",
          installDir.toString
        ).call(
          timeout = setupTimeout,
          stdout = os.Inherit
        )
        System.err.println(
          s"[${java.time.Instant.now()}] cs setup done"
        )

        // See coursier.cli.setup.DefaultAppList
        for (
          app <-
            List("cs", "coursier", "scala", "scalac", "scala-cli", "sbt", "sbtn", "scalafmt")
        )
          assert(os.exists(installDir / app) || os.exists(installDir / s"$app.bat"))
      }
    }

    test("alpine-linux") {
      if (hasDocker && !isNative) alpineLinuxTest(isNative = false)
      else if (hasDocker && isNative && isNativeStatic) alpineLinuxTest(isNative = true)
      else "Docker test disabled"
    }
  }

  def alpineLinuxTest(isNative: Boolean): Unit =
    TestUtil.withTempDir { tmpDir0 =>
      val tmpDir = os.Path(tmpDir0, os.pwd)
      os.copy(
        os.Path(if (isNative) launcher else assembly),
        tmpDir / (if (isNative) "cs" else "cs.jar")
      )

      val args = Seq[os.Shellable](
        "setup",
        "--yes"
      )

      val baseCommand =
        if (isNative)
          Seq[os.Shellable](
            "docker",
            "run",
            "--rm",
            "-v",
            s"$tmpDir:/shared",
            alpineImage,
            "/shared/cs"
          )
        else
          Seq[os.Shellable](
            "docker",
            "run",
            "--rm",
            "-v",
            s"$tmpDir:/shared",
            alpineJavaImage,
            "java",
            "-jar",
            "/shared/cs.jar"
          )
      os.proc(baseCommand, args).call(
        cwd = tmpDir,
        stdin = os.Inherit,
        stdout = os.Inherit,
        timeout = setupTimeout
      )
    }

}
