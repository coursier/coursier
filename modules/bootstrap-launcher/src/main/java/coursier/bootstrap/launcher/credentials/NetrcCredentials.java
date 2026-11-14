package coursier.bootstrap.launcher.credentials;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Java copy of coursier.credentials.NetrcCredentials
 *
 * Reads credentials from a netrc file, the way curl does: entries only look at the host (not the
 * scheme nor the port), the default entry matches any host, the first matching entry wins.
 */
public final class NetrcCredentials extends Credentials {

  private static final long serialVersionUID = 1L;

  /** null: the default file (COURSIER_NETRC, NETRC, ~/.netrc, or ~/_netrc on Windows) */
  private final String path;
  private final boolean optional;

  public NetrcCredentials(String path, boolean optional) {
    this.path = path;
    this.optional = optional;
  }

  public NetrcCredentials() {
    this(null, true);
  }

  @Override
  public List<DirectCredentials> get() throws IOException {
    final List<Path> candidates;
    if (path == null) {
      boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
      candidates = defaultCandidates(System::getenv, System.getProperty("user.home"), isWindows);
    } else
      candidates = Collections.singletonList(Paths.get(path));

    Path file = null;
    for (Path candidate : candidates)
      if (file == null && Files.isRegularFile(candidate))
        file = candidate;

    if (file == null) {
      if (optional)
        return Collections.emptyList();
      throw new RuntimeException("netrc file not found (looked for " + candidates + ")");
    }

    final String content;
    try {
      content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    } catch (IOException e) {
      if (optional)
        return Collections.emptyList();
      throw e;
    }

    final List<DirectCredentials> entries = new ArrayList<>();
    final String error = parseEntries(content, entries);
    if (error != null && !optional)
      throw new RuntimeException("Error parsing netrc file " + file + ": " + error);
    return entries;
  }

  public String getPath() {
    return path;
  }

  public boolean isOptional() {
    return optional;
  }

  /** Parses netrc content, throws if it has a syntax error */
  public static List<DirectCredentials> parse(String content, String origin) {
    final List<DirectCredentials> entries = new ArrayList<>();
    final String error = parseEntries(content, entries);
    if (error != null)
      throw new RuntimeException("Error parsing netrc file " + origin + ": " + error);
    return entries;
  }

  /** The files read when no path is passed, in order of preference (only the first existing one is) */
  static List<Path> defaultCandidates(Function<String, String> getEnv, String userHome, boolean isWindows) {
    String explicit = nonEmpty(getEnv.apply("COURSIER_NETRC"));
    if (explicit == null)
      explicit = nonEmpty(getEnv.apply("NETRC"));
    if (explicit != null)
      return Collections.singletonList(Paths.get(explicit));

    String home = nonEmpty(getEnv.apply("HOME"));
    if (home == null)
      home = nonEmpty(userHome);
    if (home == null)
      return Collections.emptyList();

    final List<Path> list = new ArrayList<>();
    final Path dir = Paths.get(home);
    list.add(dir.resolve(".netrc"));
    if (isWindows)
      list.add(dir.resolve("_netrc"));
    return list;
  }

  private static String nonEmpty(String s) {
    return (s == null || s.isEmpty()) ? null : s;
  }

  /**
   * Parses netrc content following the grammar curl accepts (see curl's lib/netrc.c), adding the
   * entries read before any syntax error to entries
   *
   * @return the syntax error, or null if there's none
   */
  static String parseEntries(String content, List<DirectCredentials> entries) {
    return new Parser(stripComments(content), entries).parse();
  }

  /** Drops comment lines, and blanks at the beginning of lines, like curl does upon loading */
  private static String stripComments(String content) {
    final StringBuilder b = new StringBuilder();
    int idx = 0;
    while (idx < content.length()) {
      final int nl = content.indexOf('\n', idx);
      final int end = (nl < 0) ? content.length() : nl + 1;
      int from = idx;
      while (from < end && isBlank(content.charAt(from)))
        from++;
      if (from >= end || content.charAt(from) != '#') {
        b.append(content, from, end);
        // like curl, make sure every line ends with a new line
        if (nl < 0)
          b.append('\n');
      }
      idx = end;
    }
    return b.toString();
  }

  private static boolean isBlank(char c) {
    return c == ' ' || c == '\t';
  }

  private static boolean isNewLine(char c) {
    return c == '\n' || c == '\r';
  }

  private enum Kind { EOF, LITERAL, MACHINE, DEFAULT, ACCOUNT, LOGIN, PASSWORD, MACDEF, JUNK }

  private static final class Token {
    final Kind kind;
    final String value;
    Token(Kind kind, String value) {
      this.kind = kind;
      this.value = value;
    }
  }

  private static final class SyntaxError extends Exception {
    SyntaxError(String message) {
      super(message);
    }
  }

  private static final class Parser {

    private final String s;
    private final List<DirectCredentials> entries;
    private int pos = 0;
    private Token pushed = null;

    // current entry: inEntry false means not in an entry, machine null in an entry means default
    private boolean inEntry = false;
    private String machine = null;
    private String login = null;
    private String password = null;

    Parser(String s, List<DirectCredentials> entries) {
      this.s = s;
      this.entries = entries;
    }

    private char charAt(int idx) {
      return (idx < s.length()) ? s.charAt(idx) : '\0';
    }

    private void skipBlanks() {
      boolean more = true;
      while (more && pos < s.length()) {
        while (isBlank(charAt(pos)))
          pos++;
        while (charAt(pos) == '\r')
          pos++;
        if (charAt(pos) == '\n')
          pos++;
        else
          more = false;
      }
    }

    // leaves pos at the second new line of the first empty line, like curl
    private void skipToEmptyLine() {
      boolean found = false;
      while (!found && pos < s.length()) {
        if (charAt(pos) == '\n') {
          pos++;
          while (charAt(pos) == '\r')
            pos++;
          if (charAt(pos) == '\n')
            found = true;
        } else
          pos++;
      }
    }

    private String quoted() throws SyntaxError {
      final StringBuilder b = new StringBuilder();
      boolean escape = false;
      boolean done = false;
      pos++; // opening quote
      while (!done && pos < s.length()) {
        final char c = s.charAt(pos);
        if (escape) {
          escape = false;
          switch (c) {
            case 'n': b.append('\n'); break;
            case 'r': b.append('\r'); break;
            case 't': b.append('\t'); break;
            default: b.append(c); break;
          }
        } else if (c == '\\')
          escape = true;
        else if (c == '"')
          done = true;
        else
          b.append(c);
        pos++;
      }
      if (!done)
        throw new SyntaxError("unterminated quoted value");
      return b.toString();
    }

    private Token next(boolean wantLiteral) throws SyntaxError {
      if (pushed != null) {
        final Token t = pushed;
        pushed = null;
        return t;
      }
      skipBlanks();
      if (pos >= s.length())
        return new Token(Kind.EOF, null);
      if (charAt(pos) == '"')
        return new Token(Kind.LITERAL, quoted());
      final int start = pos;
      while (pos < s.length() && !isBlank(charAt(pos)) && !isNewLine(charAt(pos)))
        pos++;
      final String value = s.substring(start, pos);
      if (wantLiteral)
        return new Token(Kind.LITERAL, value);
      switch (value.toLowerCase(Locale.ROOT)) {
        case "machine": return new Token(Kind.MACHINE, null);
        case "default": return new Token(Kind.DEFAULT, null);
        case "account": return new Token(Kind.ACCOUNT, null);
        case "login": return new Token(Kind.LOGIN, null);
        case "password": return new Token(Kind.PASSWORD, null);
        case "macdef": return new Token(Kind.MACDEF, null);
        default: return new Token(Kind.JUNK, null);
      }
    }

    private String literal() throws SyntaxError {
      final Token t = next(true);
      if (t.kind == Kind.LITERAL)
        return t.value;
      pushed = t;
      return null;
    }

    private void endEntry() {
      if (inEntry && login != null && password != null)
        entries.add(
          new DirectCredentials(machine == null ? "" : machine, login, password)
            .withHttpsOnly(false)
            .withAnyHost(machine == null)
        );
      inEntry = false;
      machine = null;
      login = null;
      password = null;
    }

    String parse() {
      try {
        Kind kind = Kind.JUNK;
        while (kind != Kind.EOF) {
          final Token token = next(false);
          kind = token.kind;
          switch (kind) {
            case EOF:
              endEntry();
              break;
            case MACHINE: {
              endEntry();
              final String name = literal();
              if (name != null) {
                inEntry = true;
                machine = name;
              }
              break;
            }
            case DEFAULT:
              endEntry();
              inEntry = true;
              break;
            case MACDEF:
              endEntry();
              skipToEmptyLine();
              break;
            case LOGIN: {
              final String value = literal();
              if (value != null && inEntry)
                login = value;
              break;
            }
            case PASSWORD: {
              final String value = literal();
              if (value != null && inEntry)
                password = value;
              break;
            }
            case ACCOUNT:
              // ignored, like curl does
              literal();
              break;
            default:
              break;
          }
        }
        return null;
      } catch (SyntaxError e) {
        return e.getMessage();
      }
    }
  }

  @Override
  public String toString() {
    return "NetrcCredentials(" + path + ", " + optional + ")";
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hashCode(path) + (optional ? 1231 : 1237);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    NetrcCredentials other = (NetrcCredentials) obj;
    return optional == other.optional && Objects.equals(path, other.path);
  }
}
