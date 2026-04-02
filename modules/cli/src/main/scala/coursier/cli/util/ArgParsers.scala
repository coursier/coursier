// Adapted from https://github.com/VirtusLab/scala-cli/blob/ca3f6e8f59562e1adcf798cd868b0233500f94f1/modules/cli/src/main/scala/scala/cli/util/ArgParsers.scala

package coursier.cli.util

import caseapp.core.argparser.{ArgParser, SimpleArgParser}

import scala.cli.config.PasswordOption

abstract class LowPriorityArgParsers {

  /** case-app [[ArgParser]] for [[PasswordOption]]
    *
    * Given a lower priority than the one for `Option[PasswordOption]`, as the latter falls back to
    * `None` when given an empty string (like in `--password ""`), while letting it be automatically
    * derived from this one (with the former parser and the generic [[ArgParser]] for `Option[T]`
    * from case-app) would fail on such empty input.
    */
  implicit lazy val passwordOptionArgParser: ArgParser[PasswordOption] =
    SimpleArgParser.from("password") { str =>
      PasswordOption.parse(str)
        .left.map(caseapp.core.Error.Other(_))
    }

}

object ArgParsers extends LowPriorityArgParsers {

  /** case-app [[ArgParser]] for `Option[PasswordOption]`
    *
    * Unlike a parser automatically derived through case-app [[ArgParser]] for `Option[T]`, the
    * parser here accepts empty input (like in `--password ""`), and returns a `None` value in that
    * case.
    */
  implicit lazy val optionPasswordOptionArgParser
    : ArgParser[Option[PasswordOption]] =
    SimpleArgParser.from("password") { str =>
      if (str.trim.isEmpty) Right(None)
      else passwordOptionArgParser(None, -1, -1, str).map(Some(_))
    }
}
