package coursier.parse

import fastparse._, NoWhitespace._
import coursier.Credentials
import coursier.util.Traverse._

object CredentialsParser {

  private def parser[_: P]: P[Credentials] = {

    def host = P(CharsWhile(c => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '-').!)
    def user = P((CharPred(c => !c.isSpaceChar && c != ':') ~ CharsWhile(_ != ':')).!)
    def realm = P(CharsWhile(_ != ')').!) // Is that ok?

    def space = P(CharPred(_.isSpaceChar))

    def password = P(AnyChar.rep.!)

    P(host ~ ("(" ~ realm ~ ")").? ~ space.rep(1) ~ user ~ ":" ~ password).map {
      case (host0, realmOpt, user0, password0) =>
        Credentials(host0, user0, password0)
          .withRealm(realmOpt)
    }
  }

  def parse(s: String): Either[String, Credentials] =
    fastparse.parse(s, parser(_)) match {
      case f: Parsed.Failure =>
        Left(f.msg)
      case Parsed.Success(v, _) =>
        Right(v)
    }

  def parseSeq(input: String): Either[String, Seq[Credentials]] =
    Predef.augmentString(input)
      .lines
      .map(_.dropWhile(_.isSpaceChar)) // not trimming chars on the right (password)
      .filter(_.nonEmpty)
      .toVector
      .eitherTraverse(parse)

}
