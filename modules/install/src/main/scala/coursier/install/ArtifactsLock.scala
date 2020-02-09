package coursier.install

import java.io.InputStream
import java.math.BigInteger
import java.nio.file.{Files, Path}
import java.security.MessageDigest

import cats.implicits._
import coursier.cache.internal.FileUtil
import coursier.util.Artifact
import dataclass.data

@data class ArtifactsLock (
  entries: Set[ArtifactsLock.Entry]
) {
  def repr: String =
    entries
      .toVector
      .map { e =>
        s"${e.url}#${e.checksumType}:${e.checksum}"
      }
      .sorted
      .mkString("\n")
}

object ArtifactsLock {

  @data class Entry(
    url: String,
    checksumType: String,
    checksum: String
  )

  def read(input: String): Either[String, ArtifactsLock] =
    input
      .split('\n')
      .map(_.trim)
      .zipWithIndex
      .filter(_._1.nonEmpty)
      .toList
      .traverse {
        case (line, lineNum) =>
          val idx = line.indexOf('#')
          if (idx < 0)
            Left(s"Malformed line ${lineNum + 1}")
          else {
            val url = line.take(idx)
            val checksumPart = line.drop(idx + 1)
            val idx0 = checksumPart.indexOf(':')
            if (idx0 < 0)
              Left(s"Malformed line ${lineNum + 1}")
            else {
              val checksumType = checksumPart.take(idx0)
              val checksum = checksumPart.drop(idx0 + 1)
              Right(Entry(url, checksumType, checksum))
            }
          }
      }
      .map { entries =>
        ArtifactsLock(entries.toSet)
      }

  private def sha1(f: Path): String = {
    val md = MessageDigest.getInstance("SHA-1")

    var is: InputStream = null
    try {
      is = Files.newInputStream(f)
      FileUtil.withContent(is, new FileUtil.UpdateDigest(md))
    } finally is.close()

    val b = md.digest()
    new BigInteger(1, b).toString(16)
  }

  def ofArtifacts(artifacts: Seq[(Artifact, Path)]): ArtifactsLock = {

    val entries = artifacts.map {
      case (a, f) =>
        Entry(a.url, "SHA-1", sha1(f))
    }

    ArtifactsLock(entries.toSet)
  }
}
