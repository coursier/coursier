package coursier.docker.vm

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
import scala.util.Using
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.collection.mutable.ListBuffer
import java.util.zip.GZIPInputStream
import java.security.MessageDigest
import java.math.BigInteger

final case class QemuFiles(
  qemu: os.Path,
  bios: Option[os.Path],
  virtioRom: os.Path
)

object QemuFiles {

  final case class Artifacts(
    qemu: Artifact,
    bios: Option[(Artifact, Artifact, String)],
    virtioRom: Artifact
  )

  object Artifacts {

    def default(
      hostOs: Os = Os.get(),
      hostOsVersion: String = sys.props.getOrElse("os.version", ""),
      hostCpu: Cpu = Cpu.get(),
      guestCpu: Cpu = Cpu.get()
    ): Task[Artifacts] = {
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

      val biosArtifactOptTask = guestCpu match {
        case Cpu.Arm64 =>
          val cache            = coursier.cache.FileCache()
          val debIndexArtifact =
            Artifact("https://deb.debian.org/debian/dists/trixie/main/binary-amd64/Packages.gz")
              .withChanging(true)
          val debIndexFileTask = cache.file(debIndexArtifact).run
            .flatMap {
              case Left(ex) => Task.fail(ex)
              case Right(f) => Task.point(os.Path(f, os.pwd))
            }
          debIndexFileTask.map { debIndexFile =>
            val packageName         = "qemu-efi-aarch64"
            val detailsFromIndexOpt = Using.resource(os.read.inputStream(debIndexFile)) { is =>
              val reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(is),
                StandardCharsets.UTF_8
              ))
              val it                             = reader.lines().iterator().asScala
              val groupIt: Iterator[Seq[String]] =
                new Iterator[Seq[String]] {
                  var nextGroup           = Option.empty[Seq[String]]
                  def tryReadNext(): Unit = {
                    val buf  = new ListBuffer[String]
                    var done = false
                    while (!done && it.hasNext) {
                      val elem = it.next().trim()
                      if (elem.isEmpty())
                        done = true
                      else
                        buf += elem
                    }
                    nextGroup = if (buf.isEmpty) None else Some(buf.result())
                  }
                  def hasNext: Boolean =
                    nextGroup.nonEmpty || {
                      tryReadNext()
                      nextGroup.nonEmpty
                    }
                  def next(): Seq[String] =
                    nextGroup match {
                      case Some(group) =>
                        nextGroup = None
                        group
                      case None =>
                        tryReadNext()
                        nextGroup.getOrElse {
                          throw new NoSuchElementException
                        }
                    }
                }
              groupIt.find(_.headOption.contains(s"Package: $packageName"))
            }
            val (biosUrl, biosSha256) = detailsFromIndexOpt match {
              case None =>
                throw new Exception(s"Cannot find package $packageName in ${debIndexArtifact.url}")
              case Some(detailsFromIndex) =>
                val url = detailsFromIndex
                  .find(_.startsWith("Filename: "))
                  .map("http://http.us.debian.org/debian/" + _.stripPrefix("Filename: "))
                  .getOrElse {
                    throw new Exception(
                      s"Entry for package $packageName in ${debIndexArtifact.url} has no filename"
                    )
                  }
                val sha256 = detailsFromIndex
                  .find(_.startsWith("SHA256: "))
                  .map(_.stripPrefix("SHA256: ").trim())
                  .getOrElse {
                    throw new Exception(
                      s"Entry for package $packageName in ${debIndexArtifact.url} has no SHA-256"
                    )
                  }
                (url, sha256)
            }
            Some((
              Artifact(
                biosUrl +
                  "!data.tar.xz" +
                  "!usr/share/qemu-efi-aarch64/QEMU_EFI.fd"
              ),
              Artifact(biosUrl),
              biosSha256
            ))
          }
        case Cpu.X86_64 =>
          Task.point(None)
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

      biosArtifactOptTask.map { biosArtifactOpt =>
        Artifacts(
          qemu = Artifact(qemuArchive + "!" + binarySubPath.toString),
          bios = biosArtifactOpt,
          virtioRom = Artifact(qemuArchive + "!" + virtioRomSubPath.toString)
        )
      }
    }
  }

  def default(
    artifactsTask: Task[Artifacts] = Artifacts.default(),
    archiveCache: ArchiveCache[Task] = ArchiveCache()
  ): Task[QemuFiles] =
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
        Seq(artifacts.virtioRom)
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
            val (qemu, bios, virtioRom) = artifacts.bios match {
              case None =>
                val Seq(qemu0, virtioRom0) = downloadResults.collect {
                  case Right(f) => os.Path(f)
                }
                (qemu0, None, virtioRom0)
              case Some((_, _, sha256)) =>
                val Seq(qemu0, biosDebPkg, bios0, virtioRom0) = downloadResults.collect {
                  case Right(f) => os.Path(f)
                }
                // FIXME Ideally, we should check the sha-256 before letting ArchiveCache unpack things on disk
                val rawDigest = Using.resource(os.read.inputStream(biosDebPkg)) { is =>
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
                  (qemu0, Some(bios0), virtioRom0)
                else
                  throw new Exception(
                    s"Invalid SHA-256 checksum for $bios0 (got $checksum, expected $sha256)"
                  )
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
