package coursier.docker.vm

import dataclass.data
import coursier.util.Artifact
import coursier.cache.ArchiveCache
import coursier.util.Task
import coursier.cache.FileCache
import coursier.cache.CacheLogger
import java.io.File
import coursier.cache.ArtifactError
import scala.util.Properties
import coursier.cache.util.Cpu
import coursier.cache.util.Os

@data class QemuFiles(
  qemu: os.Path,
  bios: Option[os.Path],
  virtioRom: os.Path
)

object QemuFiles {

  @data class Artifacts(
    qemu: Artifact,
    bios: Option[Artifact],
    virtioRom: Artifact
  )

  object Artifacts {

    def default(
      hostOs: Os = Os.get(),
      hostOsVersion: String = sys.props.getOrElse("os.version", ""),
      hostCpu: Cpu = Cpu.get(),
      guestCpu: Cpu = Cpu.get()
    ): Artifacts = {
      val qemuArchive = (hostOs, hostCpu) match {
        case (Os.Mac, Cpu.Arm64) =>
          if (hostOsVersion.startsWith("15."))
            "https://github.com/coursier/qemu/releases/download/nightly/qemu-macos-sequoia-aarch64.tar.gz"
          else if (hostOsVersion.startsWith("14."))
            "https://github.com/coursier/qemu/releases/download/nightly/qemu-macos-sonoma-aarch64.tar.gz"
          else
            sys.error(
              s"Unsupported macOS version on $hostCpu: $hostOsVersion (expected 14.*, or 15.*)"
            )
        case (Os.Mac, Cpu.X86_64) =>
          "https://github.com/coursier/qemu/releases/download/nightly/qemu-macos-ventura-amd64.tar.gz"
        case (Os.Windows, Cpu.X86_64) =>
          "https://github.com/coursier/qemu/releases/download/nightly/qemu-windows-amd64-cross.tar.gz"
        case (_: Os.Linux, Cpu.Arm64) =>
          ???
        case (_: Os.Linux, Cpu.X86_64) =>
          ???
        case _ =>
          sys.error(s"Unsupported OS and CPU combination: $hostOs / $hostCpu")
      }

      val biosArtifactOpt = guestCpu match {
        case Cpu.Arm64 =>
          Some(
            Artifact(
              // FIXME The file at that address frequently disappears, and the URL needs to be
              // updated (go to the directory, look for a file with the same name but newer date, …)
              "http://ftp.debian.org/debian/pool/main/e/edk2/qemu-efi-aarch64_2022.11-6+deb12u2_all.deb" +
                "!data.tar.xz" +
                "!usr/share/qemu-efi-aarch64/QEMU_EFI.fd"
            )
          )
        case Cpu.X86_64 =>
          None
      }

      val binarySubPath =
        if (Properties.isMac)
          guestCpu match {
            case Cpu.Arm64  => os.sub / "usr/local/bin/qemu-system-aarch64-unsigned"
            case Cpu.X86_64 => os.sub / "usr/local/bin/qemu-system-x86_64-unsigned"
          }
        else if (Properties.isWin)
          guestCpu match {
            case Cpu.Arm64  => os.sub / "qemu/qemu-system-aarch64.exe"
            case Cpu.X86_64 => os.sub / "qemu/qemu-system-x86_64.exe"
          }
        else
          sys.error(s"Unsupported OS: ${sys.props.getOrElse("os.name", "")}")
      val virtioRomSubPath =
        if (Properties.isMac) os.sub / "usr/local/share/qemu/efi-virtio.rom"
        else if (Properties.isWin) os.sub / "qemu/share/efi-virtio.rom"
        else sys.error(s"Unsupported OS: ${sys.props.getOrElse("os.name", "")}")

      Artifacts(
        qemu = Artifact(qemuArchive + "!" + binarySubPath.toString),
        bios = biosArtifactOpt,
        virtioRom = Artifact(qemuArchive + "!" + virtioRomSubPath.toString)
      )
    }
  }

  def default(
    artifacts: Artifacts = Artifacts.default(),
    archiveCache: ArchiveCache[Task] = ArchiveCache()
  ): Task[QemuFiles] = {

    def baseArtifact(art: Artifact): Artifact =
      if (art.url.contains("!")) art.withUrl(art.url.takeWhile(_ != '!'))
      else art

    def taskFor(art: Artifact): Task[Either[ArtifactError, File]] =
      if (art.url.contains("!")) archiveCache.get(art)
      else archiveCache.cache.file(art).run

    val artifacts0 = Seq(artifacts.qemu) ++ artifacts.bios.toSeq ++ Seq(artifacts.virtioRom)
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
          val (qemu, bios, virtioRom) =
            if (artifacts.bios.isEmpty) {
              val Seq(qemu0, virtioRom0) = downloadResults.collect {
                case Right(f) => os.Path(f)
              }
              (qemu0, None, virtioRom0)
            }
            else {
              val Seq(qemu0, bios0, virtioRom0) = downloadResults.collect {
                case Right(f) => os.Path(f)
              }
              (qemu0, Some(bios0), virtioRom0)
            }

          postProcessQemuBinary(qemu)

          Task.point(QemuFiles(qemu, bios, virtioRom))
      }
    }
  }

  def postProcessQemuBinary(qemu: os.Path): Unit = {
    val supportsPerms = qemu.toNIO.getFileSystem.supportedFileAttributeViews().contains("posix")
    if (supportsPerms && !qemu.toIO.canExecute())
      qemu.toIO.setExecutable(true)

    if (Properties.isMac) {
      val macMajorVersion = sys.props.get("os.version")
        .map(_.takeWhile(_.isDigit))
        .filter(_.nonEmpty)
        .map(_.toInt)
      val needsSigning =
        Cpu.get() match {
          case Cpu.X86_64 => qemu.last.contains("x86_64")
          case Cpu.Arm64  => qemu.last.contains("aarch64")
        }
      if (needsSigning) {
        val entitlementsOutput = os.proc("codesign", "-d", "--entitlements", "-", qemu)
          .call(mergeErrIntoOut = true, check = false)
          .out.text()
        val lines = entitlementsOutput
          .linesIterator
          .map(_.trim)
          .toVector
        val enabled = lines.containsSlice(
          Seq(
            "[Key] com.apple.security.hypervisor",
            "[Value]",
            "[Bool] true"
          )
        )
        if (!enabled) {
          val plistFile = os.temp(
            """<?xml version="1.0" encoding="UTF-8"?>
              |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
              |<plist version="1.0">
              |<dict>
              |    <key>com.apple.security.hypervisor</key>
              |    <true/>
              |</dict>
              |</plist>
              |""".stripMargin,
            prefix = "entitlements",
            suffix = ".plist",
            perms = "rw-------"
          )
          val res = os.proc("codesign", "--entitlements", plistFile, "--force", "-s", "-", qemu)
            .call(stderr = os.Pipe, check = false)
          if (res.exitCode != 0)
            os.proc("codesign", "--entitlements", plistFile, "--force", "-s", "-", qemu)
              .call(stdout = os.Inherit)
          os.remove(plistFile)
        }
      }
    }
  }
}
