package coursier.docker.tests

import coursier.cache.TestUtil._
import coursier.cache.util.Cpu
import coursier.cache.{ArchiveCache, DigestBasedCache, FileCache}
import coursier.docker.{DockerBuild, DockerPull, DockerRun, DockerUnpack}
import coursier.docker.vm.Vm
import io.github.alexarchambault.isterminal.IsTerminal
import utest._

import scala.util.Properties
import coursier.docker.vm.VmFiles

object DockerTests extends TestSuite {

  val cache = FileCache()

  // set to true to speed up tests locally
  // needs 'cs vm start' to have been run before that
  private def debugUseDefaultVm = false

  private def useVirtualization =
    System.getenv("CI") == null

  private var vmOpt0 =
    if (debugUseDefaultVm && !Properties.isLinux) Some(Vm.readFrom(Vm.defaultVmDir(), "default"))
    else Option.empty[Vm]

  private def vmOpt: Option[Vm] =
    vmOpt0.orElse {
      if (Properties.isLinux) None
      else
        synchronized {
          vmOpt0.orElse {
            val vmFiles =
              cache.logger.using(VmFiles.default()).unsafeRun(wrapExceptions = true)(cache.ec)
            val vmParams = Vm.Params.default().copy(
              memory = "2g",
              useVirtualization = useVirtualization
            )
            vmOpt0 = Some {
              Vm.spawn(
                "cs-tests",
                vmFiles,
                vmParams,
                Nil,
                outputTo = Some(Vm.defaultVmOutputDir() / "cs-tests")
              )
            }
            vmOpt0
          }
        }
    }

  override def utestAfterAll(): Unit = {
    for (vm <- vmOpt0 if !debugUseDefaultVm)
      vm.close()
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

    test("hello-world") {
      test("pull") {
        val res = DockerPull.pull(
          "library/hello-world",
          "latest",
          cache = cache
        )
        val (expectedConfigUrl, expectedManifestUrl, expectedLayerUrl) = Cpu.get() match {
          case Cpu.X86_64 =>
            (
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:1b44b5a3e06a9aae883e7bf25e45c100be0bb81a0e01b32de604f3ac44711634",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:2771e37a12b7bcb2902456ecf3f29bf9ee11ec348e66e8eb322d9780ad7fc2df",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:17eec7bbc9d79fa397ac95c7283ecd04d1fe6978516932a3db110c6206430809"
            )
          case Cpu.Arm64 =>
            (
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:ca9905c726f06de3cb54aaa54d4d1eade5403594e3fbfb050ccc970fd0212983",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:00abdbfd095cf666ff8523d0ac0c5776c617a50907b0c32db3225847b622ec5a",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:198f93fd5094f85a71f793fb8d8f481294d75fb80e6190abb4c6fad2b052a6b6"
            )
        }
        val expectedIndexUrl =
          "https://registry-1.docker.io/v2/library/hello-world/manifests/latest"

        val configUrl   = res.configArtifact.url
        val indexUrl    = res.indexArtifact.url
        val manifestUrl = res.manifestArtifact.url
        val layerUrls   = res.layerArtifacts.map(_.url)
        assert(expectedConfigUrl == configUrl)
        assert(expectedIndexUrl == indexUrl)
        assert(expectedManifestUrl == manifestUrl)
        assert(Seq(expectedLayerUrl) == layerUrls)
      }

      test("run") {
        val pullRes = DockerPull.pull(
          "library/hello-world",
          "latest",
          cache = cache
        )
        val res = DockerRun.run(
          cache,
          DigestBasedCache(),
          config = pullRes.config.config,
          layerFiles = () => pullRes.layerFiles.map(os.Path(_)),
          layerDirs = () => {
            val priviledgedArchiveCache = ArchiveCache.priviledged()
            DockerUnpack.unpack(priviledgedArchiveCache, pullRes.layerArtifacts)
              .map(os.Path(_))
          },
          containerName = "cs-docker-tests-hello-world",
          vmOpt = vmOpt,
          rootFsDirName = "rootfs",
          interactive = false,
          useSudo = !Properties.isWin,
          withUpperDir = None,
          useExec = false,
          stdout = os.Pipe
        )
        assert(res.exitCode == 0)
        val output = res.out.text()
        assert(output.contains("Hello from Docker!"))
      }
    }

    test("build") {
      test("simple") {
        withTmpDir { dir =>
          val dockerFileContent =
            """FROM alpine:latest
              |RUN echo "Hello world" > /message
              |RUN echo Hello
              |CMD cat /message
              |""".stripMargin
          val contextDir = dir / "context"
          os.write(contextDir / "Dockerfile", dockerFileContent, createFolders = true)
          val (config, layers) = DockerBuild.build(
            contextDir,
            None,
            vmOpt = vmOpt,
            cache = cache
          )
          val expectedLayerDigests = Seq(
            "4dca2763d064399e8b4a1844331fe5fe228b66a8228c2e7161cde2296addeb8b"
          )
          val layerDigests = layers.map(_._1.digest)
          assert(expectedLayerDigests == layerDigests)
        }
      }
    }
  }

}
