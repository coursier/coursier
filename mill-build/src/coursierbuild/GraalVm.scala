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
    "https://github.com/bell-sw/LibericaNIK/releases/download/25.0.5.0.3+1-25.0.5.0.3+6/bellsoft-liberica-vm-openjdk25.0.5.0.3+6-25.0.5.0.3+1"

  /** Archives per (OS, architecture), using the JVM index naming */
  def archives: Map[(String, String), PinnedArchive] = Map(
    ("linux", "amd64") -> PinnedArchive(
      s"$releaseUrl-linux-amd64.tar.gz",
      "127bba6fbec0e106325d7c5b9124a16bfdf6a2c787624273c145a08e65f6889d"
    ),
    ("linux", "arm64") -> PinnedArchive(
      s"$releaseUrl-linux-aarch64.tar.gz",
      "ac01657375e09552e26344e1b90068ad21161cc50cd4dc6d578e2dbf2894e843"
    ),
    ("darwin", "amd64") -> PinnedArchive(
      s"$releaseUrl-macos-amd64.zip",
      "a8d185dbe877695928fb6f06ccaa79159ad124103c78f5e61fb95d56938cf8c0"
    ),
    ("darwin", "arm64") -> PinnedArchive(
      s"$releaseUrl-macos-aarch64.zip",
      "28a427448f574b3d547f3be8a2039d49b96ebfded88d30f55c0970a8fbc175d0"
    ),
    ("windows", "amd64") -> PinnedArchive(
      s"$releaseUrl-windows-amd64.zip",
      "555c5c70864c03757f5b2322a08b30ca6c813126ede4c8ac8310bc92e041d088"
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
