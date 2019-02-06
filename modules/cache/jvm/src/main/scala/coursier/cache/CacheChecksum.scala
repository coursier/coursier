package coursier.cache

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object CacheChecksum {

  // matches md5 or sha1 or sha-256
  private val checksumPattern = Pattern.compile("^[0-9a-f]{32}([0-9a-f]{8})?([0-9a-f]{24})?")

  private def findChecksum(elems: Seq[String]): Option[BigInteger] =
    elems.collectFirst {
      case rawSum if checksumPattern.matcher(rawSum).matches() =>
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
            .filter(_.matches("[0-9a-f]+"))
            .mkString
        }
      )
    }

  def parseChecksum(content: String): Option[BigInteger] = {
    val lines = Predef
      .augmentString(content)
      .lines
      .toVector

    parseChecksumLine(lines).orElse(parseChecksumAlternative(lines))
  }

  def parseRawChecksum(content: Array[Byte]): Option[BigInteger] =
    if (content.length == 16 || content.length == 20)
      Some(new BigInteger(content))
    else {
      val s = new String(content, StandardCharsets.UTF_8)
      val lines = Predef
        .augmentString(s)
        .lines
        .toVector

      parseChecksumLine(lines).orElse(parseChecksumAlternative(lines))
    }

}
