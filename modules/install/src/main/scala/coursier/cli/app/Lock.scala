package coursier.cli.app

import cats.implicits._

final case class Lock (
  entries: Set[Lock.Entry]
) {
  def repr: String =
    entries
      .toVector
      .sortBy(e => Lock.Entry.unapply(e).get)
      .map { e =>
        s"${e.url}#${e.checksumType}:${e.checksum}"
      }
      .mkString("\n")
}

object Lock {
  final case class Entry(url: String, checksumType: String, checksum: String)

  def read(input: String): Either[String, Lock] = {
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
      .right
      .map { entries =>
        Lock(entries.toSet)
      }
  }
}
