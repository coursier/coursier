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
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:e2ac70e7319a02c5a477f5825259bd118b94e8b02c279c67afa63adab6d8685b",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:d1a8d0a4eeb63aff09f5f34d4d80505e0ba81905f36158cc3970d8e07179e59e",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:4f55086f7dd096d48b0e49be066971a8ed996521c2e190aa21b2435a847198b4"
            )
          case Cpu.Arm64 =>
            (
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:eb84fdc6f2a3a064445bb2a2fbc89c515666c428d6c96b6ab68a4cd218819688",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:5099b89d7666cc2186cad769ddc262ddc7c335b33f5fe79f9ffe50a01282b23e",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:58dee6a49ef1c01bb8a00180d70f55b3527c8e7326a05b3c5135c4ff60cfb6d6"
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
