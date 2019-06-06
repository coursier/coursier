package coursier.cli.publish.version

import java.io.{BufferedReader, File, InputStreamReader}

import caseapp._

object Version extends CaseApp[Options] {

  private def output(cmd: Seq[String], dir: File): String = {
    // adapted from https://stackoverflow.com/a/16714180/3714539
    val b = new ProcessBuilder(cmd: _*).directory(dir)
    val p = b.start()
    val reader = new BufferedReader(new InputStreamReader(p.getInputStream))
    val builder = new StringBuilder
    var line: String = null
    while ({
      line = reader.readLine()
      line != null
    }) {
      builder.append(line)
      builder.append(System.getProperty("line.separator"))
    }
    val retCode = p.waitFor()
    if (retCode == 0)
      builder.toString
    else
      throw new Exception(s"Command ${cmd.mkString(" ")} exited with code $retCode")
  }

  def version(dir: File): Either[String, String] = {

    val tag = output(Seq("git", "describe", "--tags", "--match", "v[0-9]*", "--abbrev=0"), dir).trim

    if (tag.isEmpty)
      Left("No git tag like v[0-9]* found")
    else {
      val dist = output(Seq("git", "rev-list", "--count", s"$tag...HEAD"), dir).trim.toInt // can throw…

      if (dist == 0)
        Right(tag)
      else {

        val previousVersion = tag.stripPrefix("v")

        // Tweak coursier.core.Version.Tokenizer to help here?

        val versionOpt =
          if (previousVersion.forall(c => c == '.' || c.isDigit)) {
            val l = previousVersion.split('.')
            Some((l.init :+ (l.last.toInt + 1).toString).mkString(".") + "-SNAPSHOT")
          } else {
            val idx = previousVersion.indexOf("-M")
            if (idx < 0)
              None
            else {
              Some(previousVersion.take(idx) + "-SNAPSHOT")
            }
          }

        versionOpt.toRight {
          s"Don't know how to handle version $previousVersion"
        }
      }
    }
  }

  def run(options: Options, remainingArgs: RemainingArgs): Unit = {

    val dir = remainingArgs.all match {
      case Seq() => new File(".")
      case Seq(path) => new File(path)
      case other =>
        Console.err.println(s"Too many arguments specified: ${other.mkString(" ")}\nExpected 0 or 1 argument.")
        sys.exit(1)
    }

    version(dir) match {
      case Left(msg) =>
        Console.err.println(msg)
        sys.exit(1)
      case Right(v) =>

        if (options.isSnapshot) {
          val retCode =
            if (v.endsWith("-SNAPSHOT"))
              0
            else
              1
          if (!options.quiet)
            Console.err.println(v)
          sys.exit(retCode)
        } else
          println(v)
    }
  }
}
