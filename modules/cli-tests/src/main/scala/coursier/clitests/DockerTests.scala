package coursier.clitests

import utest._

import java.io.File

import scala.concurrent.duration.DurationInt
import scala.util.Properties
import scala.util.control.NonFatal

abstract class DockerTests extends TestSuite {

  def launcher: String

  // set to true to speed up tests locally
  // needs 'cs vm start' to have been run before that
  private def debugUseDefaultVm = false

  private def useVirtualization =
    System.getenv("CI") == null

  private val vmStartTimeout = 5.minutes.toMillis
  private val vmStopTimeout  = 2.minutes.toMillis
  private val dockerTimeout  = 5.minutes.toMillis

  private var vmOpt0: Option[(String, Option[os.Path])] =
    if (debugUseDefaultVm && !Properties.isLinux) Some(("default", None))
    else None

  private def stopVm(id: String): Unit =
    os.proc(launcher, "vm", "stop", "--id", id).call(
      stdin = os.Inherit,
      stdout = os.Inherit,
      timeout = vmStopTimeout,
      check = false
    )

  private def vmOpt: Option[(String, Option[os.Path])] =
    vmOpt0.orElse {
      if (Properties.isLinux) None
      else
        synchronized {
          vmOpt0.orElse {
            val id     = "cs-cli-tests"
            val tmpDir = os.temp.dir(prefix = "cs-cli-tests-docker")
            os.makeDir.all(tmpDir / "cache")
            os.makeDir.all(tmpDir / "arc")
            os.makeDir.all(tmpDir / "digest")
            os.makeDir.all(tmpDir / "priv")
            try {
              os.proc(
                launcher,
                "vm",
                "start",
                "--id",
                id,
                "--memory",
                "2g",
                s"--virtualization=$useVirtualization",
                "--cache",
                tmpDir / "cache",
                "--default-cache-for-vm-files"
              ).call(
                stdin = os.Inherit,
                stdout = os.Inherit,
                env = Map(
                  "COURSIER_PRIVILEDGED_ARCHIVE_CACHE" -> (tmpDir / "priv").toString,
                  "COURSIER_DIGEST_BASED_CACHE"        -> (tmpDir / "digest").toString,
                  "COURSIER_ARCHIVE_CACHE"             -> (tmpDir / "arc").toString
                ),
                timeout = vmStartTimeout
              )
              vmOpt0 = Some((id, Some(tmpDir)))
              vmOpt0
            }
            catch {
              case NonFatal(t) =>
                try stopVm(id)
                catch {
                  case NonFatal(e) =>
                    System.err.println(s"Error stopping VM $id after failed start: $e")
                }
                finally os.remove.all(tmpDir)
                throw t
            }
          }
        }
    }

  private def vmArgs: Seq[String] =
    vmOpt.toSeq.flatMap {
      case (id, tmpDirOpt) =>
        val cacheArgs = tmpDirOpt.toSeq.flatMap { tmpDir =>
          Seq("--cache", (tmpDir / "cache").toString)
        }
        Seq("--vm", id) ++ cacheArgs
    }
  private def vmEnv: Map[String, String] =
    vmOpt
      .iterator
      .flatMap {
        case (_, tmpDirOpt) =>
          tmpDirOpt.iterator
      }
      .flatMap { tmpDir =>
        Iterator(
          "COURSIER_PRIVILEDGED_ARCHIVE_CACHE" -> (tmpDir / "priv").toString,
          "COURSIER_DIGEST_BASED_CACHE"        -> (tmpDir / "digest").toString,
          "COURSIER_ARCHIVE_CACHE"             -> (tmpDir / "arc").toString
        )
      }
      .toMap

  override def utestAfterAll(): Unit =
    for ((id, tmpDirOpt) <- vmOpt0 if !debugUseDefaultVm) {
      System.err.println(s"Stopping VM $id")
      stopVm(id)
      for (tmpDir <- tmpDirOpt)
        os.remove.all(tmpDir)
    }

  val tests =
    if (Properties.isLinux || Properties.isMac)
      actualTests
    else
      Tests {
        test("disabled on Windows") {
          "disabled"
        }
      }

  def actualTests = Tests {
    test("pull") {
      val res = os.proc(
        launcher,
        "docker",
        "pull",
        "library/hello-world:latest",
        vmArgs
      ).call(env = vmEnv, timeout = dockerTimeout)
      pprint.err.log(res.out.text())
    }

    test("run") {
      val res = os.proc(
        launcher,
        "docker",
        "run",
        "library/hello-world:latest",
        vmArgs
      ).call(env = vmEnv, timeout = dockerTimeout)
      val output = res.out.text()
      assert(output.contains("Hello from Docker!"))
    }
  }

}
