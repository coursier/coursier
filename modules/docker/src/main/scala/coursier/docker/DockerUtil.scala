package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromArray}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import coursier.cache.CacheDefaults
import coursier.cache.internal.{FileUtil, Retry}

import java.io.{File, IOException}
import java.math.BigInteger
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale

import scala.util.Using

import scala.concurrent.duration.DurationInt

object DockerUtil {

  private def tokenRetry =
    Retry(
      CacheDefaults.retryCount,
      1.second,
      CacheDefaults.retryBackoffMultiplier
    )

  def token(
    authRegistry: String,
    repoName: String
  ): String = {
    // FIXME repo escaping!!!!
    val url = s"$authRegistry?service=registry.docker.io&scope=repository:$repoName:pull"
    val b = tokenRetry.retry {
      val conn = new URL(url).openConnection()
      FileUtil.readFully(conn.getInputStream())
    } {
      case _: IOException =>
    }
    readFromArray(b)(tokenResponseCodec).token
  }

  private val digestAlgorithms = Map(
    "sha256" -> ("SHA-256", 64),
    "sha512" -> ("SHA-512", 128)
  )

  /** Checks that the content of `file` matches an OCI digest, like `sha256:0123…`
    *
    * If it doesn't, `file` is deleted, so that it gets downloaded again later on, and an exception
    * is thrown.
    */
  def checkDigest(file: File, digest: String): Unit = {
    val (algorithm, expected) = digest.split(":", 2) match {
      case Array(algo, hex) =>
        digestAlgorithms.get(algo) match {
          case Some((javaAlgo, length))
              if hex.length == length && hex.forall(c =>
                (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
              ) =>
            (javaAlgo, hex)
          case _ =>
            throw new IllegalArgumentException(s"Malformed or unsupported digest '$digest'")
        }
      case _ =>
        throw new IllegalArgumentException(s"Malformed digest '$digest'")
    }
    val md = MessageDigest.getInstance(algorithm)
    Using.resource(Files.newInputStream(file.toPath)) { is =>
      val buf  = Array.ofDim[Byte](64 * 1024)
      var read = 0
      while ({
        read = is.read(buf)
        read >= 0
      })
        md.update(buf, 0, read)
    }
    val actual =
      String.format(Locale.ROOT, s"%0${expected.length}x", new BigInteger(1, md.digest()))
    if (actual != expected) {
      Files.deleteIfExists(file.toPath)
      throw new Exception(
        s"Digest mismatch for $file: expected $digest, got ${digest.takeWhile(_ != ':')}:$actual " +
          "(file deleted)"
      )
    }
  }

  private val blobDigestRegex = "/blobs/([a-z0-9]+:[0-9a-f]+)$".r.unanchored

  /** The digest of a registry blob, from its URL (`…/v2/<name>/blobs/<digest>`) */
  def blobDigest(url: String): Option[String] =
    url match {
      case blobDigestRegex(digest) => Some(digest)
      case _                       => None
    }

  def repoNameVersion(imageName: String): (String, String) = {

    val (repoName0, repoVersion) =
      if (imageName.count(_ == ':') <= 1)
        imageName.split(':') match {
          case Array(name)      => (name, "latest")
          case Array(name, ver) => (name, ver)
        }
      else
        sys.error(s"Malformed docker repo '$imageName' (expected name:version)")

    val repoName =
      if (repoName0.contains("/")) repoName0
      else "library/" + repoName0

    (repoName, repoVersion)
  }

  def repoNameVersion(args: Seq[String]): (String, String) =
    args match {
      case Seq() =>
        sys.error("No repository specified on the command-line")
      case Seq(repo0) =>
        repoNameVersion(repo0)
      case other =>
        sys.error("Too many arguments passed as repository, expected one")
    }

  private final case class TokenResponse(
    token: String
  )

  private implicit lazy val tokenResponseCodec: JsonValueCodec[TokenResponse] =
    JsonCodecMaker.make

}
