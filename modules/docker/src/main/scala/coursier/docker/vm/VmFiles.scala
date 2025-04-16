package coursier.docker.vm

import coursier.cache.ArchiveCache
import coursier.util.Task
import coursier.util.Artifact
import coursier.cache.ArtifactError
import java.io.File
import coursier.cache.util.Cpu
import scala.util.Using
import java.math.BigInteger
import java.security.MessageDigest

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
    artifactsTask: Task[QemuFiles.Artifacts] = QemuFiles.Artifacts.default(),
    imageArtifact: Artifact = defaultImageArtifact(),
    archiveCache: ArchiveCache[Task] = ArchiveCache()
  ): Task[VmFiles] =
    // FIXME Quite some duplication with QemuFiles.default
    artifactsTask.flatMap { artifacts =>
      def baseArtifact(art: Artifact): Artifact =
        if (art.url.contains("!")) art.withUrl(art.url.takeWhile(_ != '!'))
        else art

      def taskFor(art: Artifact): Task[Either[ArtifactError, File]] =
        if (art.url.contains("!")) archiveCache.get(art)
        else archiveCache.cache.file(art).run

      val artifacts0 = Seq(artifacts.qemu) ++
        artifacts.bios.map(_._2).toSeq ++
        artifacts.bios.map(_._1).toSeq ++
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
            val (qemu, bios, virtioRom, image) = artifacts.bios match {
              case None =>
                val Seq(qemu0, virtioRom0, image0) = downloadResults.collect {
                  case Right(f) => os.Path(f)
                }
                (qemu0, None, virtioRom0, image0)
              case Some((_, _, sha256)) =>
                val Seq(qemu0, biosDepPkg, bios0, virtioRom0, image0) = downloadResults.collect {
                  case Right(f) => os.Path(f)
                }
                // FIXME Ideally, we should check the sha-256 before letting ArchiveCache unpack things on disk
                val rawDigest = Using.resource(os.read.inputStream(biosDepPkg)) { is =>
                  val md   = MessageDigest.getInstance("SHA-256")
                  val buf  = Array.ofDim[Byte](64 * 1024)
                  var read = 0
                  while ({
                    read = is.read(buf)
                    read >= 0
                  })
                    md.update(buf, 0, read)
                  md.digest()
                }
                val rawChecksum = new BigInteger(1, rawDigest).toString(16)
                val checksum    = "0" * (64 - rawChecksum.length) + rawChecksum
                if (checksum == sha256)
                  (qemu0, Some(bios0), virtioRom0, image0)
                else
                  throw new Exception(
                    s"Invalid SHA-256 checksum for $bios0 (got $checksum, expected $sha256)"
                  )
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
