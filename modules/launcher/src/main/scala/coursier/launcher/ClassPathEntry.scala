package coursier.launcher

import dataclass.{data, since => unroll}

import java.math.BigInteger
import java.nio.file.{Files, Path}
import java.security.MessageDigest

import scala.util.Using

sealed abstract class ClassPathEntry extends Product with Serializable

object ClassPathEntry {

  /** A class path entry that bootstrap launchers download at run time
    *
    * @param url
    *   the URL of the JAR
    * @param sha256
    *   if non-empty, the hex-encoded SHA-256 of the JAR, that bootstrap launchers check the
    *   downloaded or cached file against
    */
  @data(
    deprecatedSetters = true,
    deprecatedSettersMessage = "Use copy instead",
    deprecatedSettersSince = "2.1.25"
  ) case class Url(
    url: String,
    @unroll
    sha256: Option[String] = None
  ) extends ClassPathEntry
  @data(
    deprecatedSetters = true,
    deprecatedSettersMessage = "Use copy instead",
    deprecatedSettersSince = "2.1.25"
  ) case class Resource(
    fileName: String,
    lastModified: Long,
    content: Array[Byte]
  ) extends ClassPathEntry

  /** Hex-encoded SHA-256 of a file, in the format `Url.sha256` expects */
  def sha256(file: Path): String = {
    val md = MessageDigest.getInstance("SHA-256")
    Using.resource(Files.newInputStream(file)) { is =>
      val buf  = Array.ofDim[Byte](16384)
      var read = -1
      while ({
        read = is.read(buf)
        read >= 0
      })
        md.update(buf, 0, read)
    }
    String.format("%064x", new BigInteger(1, md.digest()))
  }

}
