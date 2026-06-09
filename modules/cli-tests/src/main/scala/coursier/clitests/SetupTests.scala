package coursier.clitests

import utest._

import java.nio.file.Paths

import scala.concurrent.duration.DurationInt
import scala.util.Properties

abstract class SetupTests extends TestSuite {

  // Fail eagerly rather than letting a stuck process hang until the whole CI
  // job times out (and gets force-killed, leaving orphan processes behind).
  private val setupTimeout = 10.minutes.toMillis

  def launcher: String
  def assembly: os.Path
  def isNative: Boolean
  def isNativeStatic: Boolean
  def isStandalone: Boolean

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

    // Run the same setup test twice: once against a local snapshot of the
    // io.get-coursier:apps channel (so that it doesn't depend on the
    // coursier/apps repository being reachable), and once against the live
    // default channel.
    test("setup-local-channel") {
      setupTest(useLocalChannel = true)
    }

    test("setup") {
      setupTest(useLocalChannel = false)
    }

    test("alpine-linux") {
      if (hasDocker)
        if (isNativeStatic) alpineLinuxTest(isNative = true)
        else if (!isNative && !isStandalone) alpineLinuxTest(isNative = false)
        else "Docker test disabled (native non-static launcher)"
      else
        "Docker test disabled (docker unavailable)"
    }
  }

  // Apps installed by `cs setup`, see coursier.cli.setup.DefaultAppList
  private def setupApps =
    List("cs", "coursier", "scala", "scalac", "scala-cli", "sbt", "sbtn", "scalafmt")

  // Directory containing a snapshot of the io.get-coursier:apps channel
  // (bundled as test resources under setup-channel/). Lets the setup test use a
  // local channel instead of depending on the coursier/apps repository.
  private def localChannelDir: os.Path = {
    val resourcePath = "setup-channel/cs.json"
    val url          = Thread.currentThread().getContextClassLoader.getResource(resourcePath)
    Predef.assert(url != null, s"Resource $resourcePath not found")
    Predef.assert(url.getProtocol == "file", s"Resource $resourcePath is not a file ($url)")
    os.Path(Paths.get(url.toURI)) / os.up
  }

  def setupTest(useLocalChannel: Boolean): Unit =
    TestUtil.withTempDir { tempDir =>
      val homeDir    = os.Path(tempDir, os.pwd)
      val installDir = homeDir / "bin"

      val channelArgs =
        if (useLocalChannel)
          Seq[os.Shellable](
            "--channel",
            localChannelDir.toString,
            "--default-channels=false"
          )
        else
          Nil

      // TODO Temporary diagnostics: run verbosely and stream the command's output live
      // (rather than capturing it), with timestamps around the call, so that if `cs setup`
      // stalls again we can tell from the CI logs where it got stuck. Remove once the
      // standalone setup hang is understood.
      System.err.println(s"[${java.time.Instant.now()}] Running cs setup")
      os.proc(
        launcher,
        "setup",
        "--yes",
        "-v",
        "-v",
        "--user-home",
        homeDir.toString,
        "--install-dir",
        installDir.toString,
        channelArgs
      ).call(
        timeout = setupTimeout,
        stdout = os.Inherit
      )
      System.err.println(
        s"[${java.time.Instant.now()}] cs setup done"
      )

      for (app <- setupApps)
        assert(os.exists(installDir / app) || os.exists(installDir / s"$app.bat"))
    }

  def alpineLinuxTest(isNative: Boolean): Unit =
    TestUtil.withTempDir { tmpDir0 =>
      val tmpDir = os.Path(tmpDir0, os.pwd)
      os.copy(
        if (isNative) os.Path(launcher) else assembly,
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
