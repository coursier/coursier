package coursierbuild

import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale

import scala.util.Using

/** The GraalVM the native launchers are built with
  *
  * Its archives are pinned by URL and SHA-256, and checked before being extracted, rather than
  * looked up in the JVM index, so that a tampered index entry or download can't make its way into
  * the released launchers.
  *
  * To bump it, update `releaseUrl` and the checksums below. The checksums are the `digest` fields
  * of the GitHub release assets (`gh api repos/bell-sw/LibericaNIK/releases/tags/<tag> --jq
  * '.assets[] | "\(.digest) \(.name)"'`), that should match the `sha256sum` of the downloaded
  * archives, and their SHA-1 should match the one BellSoft publishes
  * (`https://api.bell-sw.com/v1/nik/releases?output=json`). Keep `Versions.graalVmJvmId` in sync
  * too.
  */
object GraalVm {

  final case class PinnedArchive(url: String, sha256: String) {

    /** Downloads this archive, checks its SHA-256, and extracts it
      *
      * @return
      *   the Java home directory in the extracted archive
      */
    def javaHome(): os.Path = {
      import coursier.cache.{ArchiveCache, FileCache}
      import coursier.jvm.JvmCache
      import coursier.util.{Artifact, Task => CsTask}

      val fileCache = FileCache[CsTask]()

      val archiveFile = fileCache.file(Artifact(url)).run
        .unsafeRun(wrapExceptions = true)(using fileCache.ec)
        .fold(e => throw e, f => os.Path(f.getAbsolutePath))
      val actualSha256 = GraalVm.sha256(archiveFile)
      if (actualSha256 != sha256) {
        // so that the next attempt downloads it again
        os.remove(archiveFile)
        sys.error(
          s"Invalid SHA-256 for $url: expected $sha256, got $actualSha256 " +
            s"(removed $archiveFile)"
        )
      }

      // Finds the file checked above in the cache, and extracts it if it hasn't been already
      val jvmCache = JvmCache().copy(archiveCache = ArchiveCache().copy(cache = fileCache))
      val home     = jvmCache.get(url).unsafeRun(wrapExceptions = true)(using fileCache.ec)
      os.Path(home.getAbsolutePath)
    }
  }

  private def releaseUrl =
    "https://github.com/bell-sw/LibericaNIK/releases/download/25.0.2+1-25.0.2+13/bellsoft-liberica-vm-openjdk25.0.2+13-25.0.2+1"

  /** Archives per (OS, architecture), using the JVM index naming */
  def archives: Map[(String, String), PinnedArchive] = Map(
    ("linux", "amd64") -> PinnedArchive(
      s"$releaseUrl-linux-amd64.tar.gz",
      "fe9d71a107e523a19ec2eddf77618cca7c98fe6fdfa609f4dc93df7d798429d0"
    ),
    ("linux", "arm64") -> PinnedArchive(
      s"$releaseUrl-linux-aarch64.tar.gz",
      "0b022d9fd134677a1cacccb2e26fcd3353f08876232a327148c67c3de096f5c3"
    ),
    ("darwin", "amd64") -> PinnedArchive(
      s"$releaseUrl-macos-amd64.zip",
      "7cf1c0e361c2c12977d8946114b4ce5be6ce42ec5371390b2f1e4399381bb718"
    ),
    ("darwin", "arm64") -> PinnedArchive(
      s"$releaseUrl-macos-aarch64.zip",
      "ac98d7357f96d755a373f209918e4c4509163712d65156631ed600f4ba90025a"
    ),
    ("windows", "amd64") -> PinnedArchive(
      s"$releaseUrl-windows-amd64.zip",
      "053784c7b970186a0fe8e1bc2134b265d6fb8008aff1e330c6637fee18fa49e1"
    )
  )

  private def currentPlatform: (String, String) = {
    val osName = System.getProperty("os.name").toLowerCase(Locale.ROOT)
    val os =
      if (osName.contains("linux")) "linux"
      else if (osName.contains("mac")) "darwin"
      else if (osName.contains("windows")) "windows"
      else osName
    val arch = sys.props("os.arch").toLowerCase(Locale.ROOT) match {
      case "x86_64" | "amd64"  => "amd64"
      case "aarch64" | "arm64" => "arm64"
      case other               => other
    }
    (os, arch)
  }

  def currentArchive: PinnedArchive = {
    val platform = currentPlatform
    archives.getOrElse(
      platform,
      sys.error(
        s"No pinned GraalVM archive for ${platform._1} / ${platform._2}, " +
          "set GRAALVM_HOME to use a GraalVM of your own"
      )
    )
  }

  private def sha256(file: os.Path): String = {
    val md = MessageDigest.getInstance("SHA-256")
    Using.resource(os.read.inputStream(file)) { is =>
      val buf  = new Array[Byte](64 * 1024)
      var read = 0
      while ({ read = is.read(buf); read >= 0 })
        md.update(buf, 0, read)
    }
    String.format("%064x", new BigInteger(1, md.digest()))
  }

  /** Downloads the archive of the current platform, checks its SHA-256, and extracts it
    *
    * @return
    *   the GraalVM home directory
    */
  def javaHome(): os.Path =
    currentArchive.javaHome()
}
