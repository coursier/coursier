package coursier.docker.vm

import coursier.cache.ArchiveCache
import coursier.util.Task
import coursier.util.Artifact
import coursier.cache.ArtifactError
import java.io.File
import coursier.cache.util.Cpu

final case class VmFiles(
  qemu: QemuFiles,
  image: os.Path
)

object VmFiles {

  def defaultImageArtifact(guestCpu: Cpu = Cpu.get()): Artifact =
    guestCpu match {
      case Cpu.X86_64 =>
        Artifact(
          "https://cloud-images.ubuntu.com/minimal/releases/noble/release/ubuntu-24.04-minimal-cloudimg-amd64.img"
        )
      case Cpu.Arm64 =>
        Artifact(
          "https://cloud-images.ubuntu.com/minimal/releases/noble/release/ubuntu-24.04-minimal-cloudimg-arm64.img"
        )
    }

  def default(
    artifacts: QemuFiles.Artifacts = QemuFiles.Artifacts.default(),
    imageArtifact: Artifact = defaultImageArtifact(),
    archiveCache: ArchiveCache[Task] = ArchiveCache()
  ): Task[VmFiles] = {

    // FIXME Quite some duplication with QemuFiles.default

    def baseArtifact(art: Artifact): Artifact =
      if (art.url.contains("!")) art.withUrl(art.url.takeWhile(_ != '!'))
      else art

    def taskFor(art: Artifact): Task[Either[ArtifactError, File]] =
      if (art.url.contains("!")) archiveCache.get(art)
      else archiveCache.cache.file(art).run

    val artifacts0 = Seq(artifacts.qemu) ++
      artifacts.bios.toSeq ++
      Seq(
        artifacts.virtioRom,
        imageArtifact
      )
    val downloadTask =
      // First download every artifact with 'distinct', to work around "Attempts to download … twice" issues
      // (coursier logger not accepting concurrent downloads of the same URL)
      Task.gather.gather(artifacts0.map(baseArtifact).distinct.map(taskFor)).flatMap { _ =>
        Task.gather.gather(artifacts0.map(taskFor))
      }

    downloadTask.flatMap { downloadResults =>
      val errors = downloadResults.collect {
        case Left(err) => err
      }
      errors match {
        case Seq(first, others @ _*) =>
          for (other <- others)
            first.addSuppressed(other)
          Task.fail(new Exception(first))
        case Seq() =>
          val (qemu, bios, virtioRom, image) =
            if (artifacts.bios.isEmpty) {
              val Seq(qemu0, virtioRom0, image0) = downloadResults.collect {
                case Right(f) => os.Path(f)
              }
              (qemu0, None, virtioRom0, image0)
            }
            else {
              val Seq(qemu0, bios0, virtioRom0, image0) = downloadResults.collect {
                case Right(f) => os.Path(f)
              }
              (qemu0, Some(bios0), virtioRom0, image0)
            }

          QemuFiles.postProcessQemuBinary(qemu)

          Task.point(
            VmFiles(
              QemuFiles(qemu, bios, virtioRom),
              image
            )
          )
      }
    }
  }
}
