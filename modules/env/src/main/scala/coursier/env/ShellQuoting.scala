package coursier.env

/** Quoting of the values written in shell scripts and profile files.
  *
  * Values (JVM paths in particular) may come from the content of downloaded archives, so these
  * escape everything a shell might interpret, so that the variable gets the exact value and no
  * command can be run by sourcing the generated snippets.
  */
private[coursier] object ShellQuoting {

  private def checkName(name: String): Unit =
    if (
      name.isEmpty || !name.forall(c => c == '_' || (c < 128 && c.isLetterOrDigit)) ||
      name.head.isDigit
    )
      throw new IllegalArgumentException(s"Invalid environment variable name: '$name'")

  private def checkValue(name: String, value: String): Unit =
    if (value.exists(c => c == '\n' || c == '\r' || c == '\u0000'))
      throw new IllegalArgumentException(
        s"Invalid value for environment variable $name (contains a new line or NUL character): " +
          value.replace("\n", "\\n").replace("\r", "\\r").replace("\u0000", "\\0")
      )

  private def escape(value: String, special: Char => Boolean): String =
    if (value.exists(special))
      value.flatMap(c => if (special(c)) "\\" + c else c.toString)
    else
      value

  /** Escapes a value, so that it can be put between double quotes in sh / bash / zsh */
  def posix(name: String, value: String): String = {
    checkName(name)
    checkValue(name, value)
    escape(value, c => c == '"' || c == '$' || c == '`' || c == '\\')
  }

  /** Escapes a value, so that it can be put between double quotes in fish */
  def fish(name: String, value: String): String = {
    checkName(name)
    checkValue(name, value)
    escape(value, c => c == '"' || c == '$' || c == '\\')
  }

  /** Validates a value meant to be put in `set "NAME=value"` in a batch script
    *
    * Double quotes can't be escaped there, and can't be part of Windows paths anyway.
    */
  def bat(name: String, value: String): String = {
    checkName(name)
    checkValue(name, value)
    if (value.contains('"'))
      throw new IllegalArgumentException(
        s"Invalid value for environment variable $name (contains a double quote): $value"
      )
    value
  }

  /** Quotes a value as a PowerShell single-quoted string literal, quotes included */
  def powershellLiteral(value: String): String = {
    // PowerShell accepts typographic single quotes as single quotes too
    def isQuote(c: Char) = c == '\'' || c == '\u2018' || c == '\u2019' || c == '\u201A' ||
      c == '\u201B'
    "'" + value.flatMap(c => if (isQuote(c)) s"$c$c" else c.toString) + "'"
  }

}
