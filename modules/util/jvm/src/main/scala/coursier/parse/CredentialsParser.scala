package coursier.parse

import coursier.credentials.DirectCredentials
import coursier.util.Traverse._
import coursier.util.ValidationNel

object CredentialsParser {

  private val pattern = """([a-zA-Z0-9.-]*)(\(.*\))?[ ]+([^ :][^:]*):(.*)""".r.pattern

  def parse(s: String): Either[String, DirectCredentials] = {
    val m = pattern.matcher(s)
    if (m.matches()) {
      val cred = DirectCredentials(m.group(1), m.group(3), m.group(4))
          .withRealm(Option(m.group(2)).map(_.stripPrefix("(").stripSuffix(")")))
      Right(cred)
    } else
      Left("Malformed credentials") // FIXME More precise error message?
  }

  def parseSeq(input: String): ValidationNel[String, Seq[DirectCredentials]] =
    Predef.augmentString(input)
      .lines
      .map(_.dropWhile(_.isSpaceChar)) // not trimming chars on the right (password)
      .filter(_.nonEmpty)
      .toVector
      .validationNelTraverse { s =>
        val e = parse(s)
        ValidationNel.fromEither(e)
      }

}
