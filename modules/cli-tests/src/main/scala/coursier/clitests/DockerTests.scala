package coursier.clitests

import utest._

import java.io.File

import scala.util.Properties

abstract class DockerTests extends TestSuite {

  def launcher: String

  // set to true to speed up tests locally
  // needs 'cs vm start' to have been run before that
  private def debugUseDefaultVm = false

  private def useVirtualization =
    System.getenv("CI") == null

  private var vmOpt0: Option[(String, Option[os.Path])] =
    if (debugUseDefaultVm && !Properties.isLinux) Some(("default", None))
    else None

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
              tmpDir / "cache"
            ).call(
              stdin = os.Inherit,
              stdout = os.Inherit,
              env = Map(
                "COURSIER_PRIVILEDGED_ARCHIVE_CACHE" -> (tmpDir / "priv").toString,
                "COURSIER_DIGEST_BASED_CACHE"        -> (tmpDir / "digest").toString,
                "COURSIER_ARCHIVE_CACHE"             -> (tmpDir / "arc").toString
              )
            )
            vmOpt0 = Some((id, Some(tmpDir)))
            vmOpt0
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
      os.proc(launcher, "vm", "stop", "--id", id)
        .call(stdin = os.Inherit, stdout = os.Inherit)
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
      ).call(env = vmEnv)
      pprint.err.log(res.out.text())
    }

    test("run") {
      val res = os.proc(
        launcher,
        "docker",
        "run",
        "library/hello-world:latest",
        vmArgs
      ).call(env = vmEnv)
      val output = res.out.text()
      assert(output.contains("Hello from Docker!"))
    }
  }

}
