package coursier.credentials

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Locale

import dataclass.{data, since}

import scala.util.Properties

/** Credentials read from a netrc file, the way curl reads them
  *
  * `path` is the file to read. If it's empty, the file is the one the `COURSIER_NETRC` environment
  * variable points at, else the one `NETRC` points at, else `.netrc` in the user home directory
  * (the `HOME` environment variable, else the `user.home` Java property), or `_netrc` there on
  * Windows if `.netrc` doesn't exist.
  *
  * If `optional` is true, a missing file is ignored, and so is anything after a syntax error in it,
  * like `curl --netrc-optional` does. Else, both are errors, like with `curl --netrc`.
  *
  * Like curl, credentials from netrc files only take the host into account, not the scheme nor the
  * port: a `machine repo.example.com` entry is sent to `https://repo.example.com:8443` and
  * `http://repo.example.com` alike. The `default` entry matches any host. The first entry that
  * matches wins, and netrc entries only come after any other credentials coursier is passed.
  *
  * Like other credentials coming from files or the environment, these are only sent once the server
  * asks for credentials, and aren't passed on redirections (the credentials for the redirection
  * target are looked up again instead).
  */
@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class NetrcCredentials(
  path: Option[String] = None,
  @since
  optional: Boolean = true
) extends Credentials {

  def get(): Seq[DirectCredentials] = {
    val candidates = path match {
      case Some(p) => Seq(Paths.get(p))
      case None =>
        NetrcCredentials.defaultCandidates(
          name => Option(System.getenv(name)),
          Option(System.getProperty("user.home")),
          Properties.isWin
        )
    }
    candidates.find(Files.isRegularFile(_)) match {
      case Some(file) =>
        val contentOpt =
          try Some(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
          catch {
            case _: IOException if optional => None
          }
        contentOpt.toSeq.flatMap { content =>
          val (entries, errorOpt) = NetrcCredentials.parseEntries(content)
          errorOpt match {
            case Some(error) if !optional =>
              throw new Exception(s"Error parsing netrc file $file: $error")
            case _ =>
              entries
          }
        }
      case None =>
        if (optional) Nil
        else
          throw new Exception(
            s"netrc file not found (looked for ${candidates.mkString(", ")})"
          )
    }
  }
}

object NetrcCredentials {

  /** Parses the content of a netrc file
    *
    * Throws if the file has a syntax error. Entries without a login or a password are ignored.
    */
  def parse(content: String, origin: String): Seq[DirectCredentials] = {
    val (entries, errorOpt) = parseEntries(content)
    for (error <- errorOpt)
      throw new Exception(s"Error parsing netrc file $origin: $error")
    entries
  }

  /** The files `NetrcCredentials` reads, in order of preference, when it's not passed a path
    *
    * Only the first existing one is read.
    */
  private[credentials] def defaultCandidates(
    getEnv: String => Option[String],
    userHome: => Option[String],
    isWindows: Boolean
  ): Seq[Path] = {
    def env(name: String) = getEnv(name).filter(_.nonEmpty)
    env("COURSIER_NETRC").orElse(env("NETRC")) match {
      case Some(p) => Seq(Paths.get(p))
      case None =>
        env("HOME").orElse(userHome.filter(_.nonEmpty)).toSeq.flatMap { home =>
          val dir = Paths.get(home)
          if (isWindows) Seq(dir.resolve(".netrc"), dir.resolve("_netrc"))
          else Seq(dir.resolve(".netrc"))
        }
    }
  }

  /** Parses netrc content following the grammar curl accepts, see curl's `lib/netrc.c`
    *
    * Returns the credentials of the entries read before any syntax error, and that error.
    */
  private[credentials] def parseEntries(
    content: String
  ): (Seq[DirectCredentials], Option[String]) =
    new Parser(stripComments(content)).parse()

  /** Drops comment lines, and blanks at the beginning of lines, like curl does upon loading */
  private def stripComments(content: String): String = {
    val b   = new StringBuilder
    var idx = 0
    while (idx < content.length) {
      val nl   = content.indexOf('\n', idx)
      val end  = if (nl < 0) content.length else nl + 1
      var from = idx
      while (from < end && isBlank(content.charAt(from)))
        from += 1
      if (from >= end || content.charAt(from) != '#') {
        b.append(content.substring(from, end))
        // like curl, make sure every line ends with a new line
        if (nl < 0) b.append('\n')
      }
      idx = end
    }
    b.result()
  }

  private def isBlank(c: Char): Boolean =
    c == ' ' || c == '\t'
  private def isNewLine(c: Char): Boolean =
    c == '\n' || c == '\r'

  private sealed abstract class Token extends Product with Serializable
  private object Token {
    case object Eof                         extends Token
    final case class Literal(value: String) extends Token
    case object Machine                     extends Token
    case object Default                     extends Token
    case object Account                     extends Token
    case object Login                       extends Token
    case object Password                    extends Token
    case object Macdef                      extends Token
    case object Junk                        extends Token
  }

  private final class SyntaxError(message: String) extends Exception(message)

  private final class Parser(s: String) {

    private var pos                   = 0
    private var pushed: Option[Token] = None

    private def charAt(idx: Int): Char =
      if (idx < s.length) s.charAt(idx) else '\u0000'

    private def skipBlanks(): Unit = {
      var more = true
      while (more && pos < s.length) {
        while (isBlank(charAt(pos)))
          pos += 1
        while (charAt(pos) == '\r')
          pos += 1
        if (charAt(pos) == '\n')
          pos += 1
        else
          more = false
      }
    }

    // leaves pos at the second new line of the first empty line, like curl
    private def skipToEmptyLine(): Unit = {
      var found = false
      while (!found && pos < s.length)
        charAt(pos) match {
          case '\n' =>
            pos += 1
            while (charAt(pos) == '\r')
              pos += 1
            if (charAt(pos) == '\n')
              found = true
          case _ =>
            pos += 1
        }
    }

    private def quoted(): String = {
      val b      = new StringBuilder
      var escape = false
      var done   = false
      pos += 1 // opening quote
      while (!done && pos < s.length) {
        val c = s.charAt(pos)
        if (escape) {
          escape = false
          b.append(c match {
            case 'n'   => '\n'
            case 'r'   => '\r'
            case 't'   => '\t'
            case other => other
          })
        }
        else if (c == '\\')
          escape = true
        else if (c == '"')
          done = true
        else
          b.append(c)
        pos += 1
      }
      if (!done)
        throw new SyntaxError("unterminated quoted value")
      b.result()
    }

    def push(token: Token): Unit =
      pushed = Some(token)

    def next(wantLiteral: Boolean): Token =
      pushed match {
        case Some(token) =>
          pushed = None
          token
        case None =>
          skipBlanks()
          if (pos >= s.length) Token.Eof
          else if (charAt(pos) == '"') Token.Literal(quoted())
          else {
            val start = pos
            while (pos < s.length && !isBlank(charAt(pos)) && !isNewLine(charAt(pos)))
              pos += 1
            val value = s.substring(start, pos)
            if (wantLiteral) Token.Literal(value)
            else
              value.toLowerCase(Locale.ROOT) match {
                case "machine"  => Token.Machine
                case "default"  => Token.Default
                case "account"  => Token.Account
                case "login"    => Token.Login
                case "password" => Token.Password
                case "macdef"   => Token.Macdef
                case _          => Token.Junk
              }
          }
      }

    def parse(): (Seq[DirectCredentials], Option[String]) = {
      val entries = Vector.newBuilder[DirectCredentials]

      // None: not in an entry, Some(None): in the default entry, Some(Some(host)): in a machine one
      var current: Option[Option[String]] = None
      var login: Option[String]           = None
      var password: Option[String]        = None

      def endEntry(): Unit = {
        for (machineOpt <- current; login0 <- login; password0 <- password)
          entries += DirectCredentials(machineOpt.getOrElse(""), login0, password0).copy(
            httpsOnly = false,
            anyHost = machineOpt.isEmpty
          )
        current = None
        login = None
        password = None
      }

      def literal(): Option[String] =
        next(wantLiteral = true) match {
          case Token.Literal(value) => Some(value)
          case other =>
            push(other)
            None
        }

      val errorOpt =
        try {
          var token: Token = Token.Junk
          while (token != Token.Eof) {
            token = next(wantLiteral = false)
            token match {
              case Token.Eof =>
                endEntry()
              case Token.Machine =>
                endEntry()
                current = literal().map(Some(_))
              case Token.Default =>
                endEntry()
                current = Some(None)
              case Token.Macdef =>
                endEntry()
                skipToEmptyLine()
              case Token.Login =>
                for (value <- literal() if current.nonEmpty)
                  login = Some(value)
              case Token.Password =>
                for (value <- literal() if current.nonEmpty)
                  password = Some(value)
              case Token.Account =>
                // ignored, like curl does
                val _ = literal()
              case Token.Literal(_) | Token.Junk =>
            }
          }
          None
        }
        catch {
          case e: SyntaxError => Some(e.getMessage)
        }

      (entries.result(), errorOpt)
    }
  }
}
