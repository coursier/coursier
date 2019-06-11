package coursier.cache

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object CacheChecksum {

  private val checksumLength = Set(
    32, // md5
    40, // sha-1
    64, // sha-256
    128 // sha-512
  )

  private def ifHexString(s: String) =
    s.forall(c => c.isDigit || c >= 'a' && c <= 'z')

  private def findChecksum(elems: Seq[String]): Option[BigInteger] =
    elems.collectFirst {
      case rawSum if ifHexString(rawSum) && checksumLength.contains(rawSum.length) =>
        new BigInteger(rawSum, 16)
    }

  private def parseChecksumLine(lines: Seq[String]): Option[BigInteger] =
    findChecksum(lines.map(_.toLowerCase.replaceAll("\\s", "")))

  private def parseChecksumAlternative(lines: Seq[String]): Option[BigInteger] =
    findChecksum(lines.flatMap(_.toLowerCase.split("\\s+"))).orElse {
      findChecksum(
        lines.map { line =>
          line
            .toLowerCase
            .split("\\s+")
            .filter(ifHexString)
            .mkString
        }
      )
    }

  def parseChecksum(content: String): Option[BigInteger] = {
    val lines = Predef.augmentString(content)
      .lines
      .toVector

    parseChecksumLine(lines).orElse(parseChecksumAlternative(lines))
  }

  def parseRawChecksum(content: Array[Byte]): Option[BigInteger] =
    if (content.length == 16 || content.length == 20)
      Some(new BigInteger(content))
    else {
      val s = new String(content, StandardCharsets.UTF_8)
      val lines = Predef.augmentString(s)
        .lines
        .toVector

      parseChecksumLine(lines) orElse parseChecksumAlternative(lines)
    }

}
