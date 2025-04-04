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
            val vmFiles = cache.logger.using(VmFiles.default()).unsafeRun()(cache.ec)
            val vmParams = Vm.Params.default()
              .withMemory("2g")
              .withUseVirtualization(useVirtualization)
            vmOpt0 = Some(Vm.spawn("cs-tests", vmFiles, vmParams, Nil))
            vmOpt0
          }
        }
    }

  override def utestAfterAll(): Unit = {
    for (vm <- vmOpt0 if !debugUseDefaultVm)
      vm.close()
  }

  def tests = Tests {

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
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:74cc54e27dc41bb10dc4b2226072d469509f2f22f1a3ce74f4a59661a1d44602",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:03b62250a3cb1abd125271d393fc08bf0cc713391eda6b57c02d1ef85efcc25c",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:e6590344b1a5dc518829d6ea1524fc12f8bcd14ee9a02aa6ad8360cce3a9a9e9"
            )
          case Cpu.Arm64 =>
            (
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:f1f77a0f96b7251d7ef5472705624e2d76db64855b5b121e1cbefe9dc52d0f86",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:a3f53a068794afb31f76ae82f79c71db0fb05a3ec960c62cd15027e214d7dc7f",
              "https://registry-1.docker.io/v2/library/hello-world/blobs/sha256:c9c5fd25a1bdc181cb012bc4fbb1ab272a975728f54064b7ae3ee8e77fd28c46"
            )
        }
        val expectedIndexUrl =
          "https://registry-1.docker.io/v2/library/hello-world/manifests/latest"

        assert(res.configArtifact.url == expectedConfigUrl)
        assert(res.indexArtifact.url == expectedIndexUrl)
        assert(res.manifestArtifact.url == expectedManifestUrl)
        assert(res.layerArtifacts.map(_.url) == Seq(expectedLayerUrl))
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
