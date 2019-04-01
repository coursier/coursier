package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.core.Configuration
import coursier.parse.DependencyParser
import coursier.{Attributes, Dependency}


final case class SharedLoaderOptions(

  @Value("target:dependency")
  @Short("I")
  @Short("isolated") // former deprecated name
  @Help("Dependencies to be put in shared class loaders")
    shared: List[String] = Nil,

  @Help("Comma-separated isolation targets")
  @Short("i")
  @Short("isolateTarget") // former deprecated name
    sharedTarget: List[String] = Nil

) {

  // the methods below should go away as soon as they're unused / the legacy Helper stuff is removed

  lazy val targetsOrExit = {
    val l = sharedTarget.flatMap(_.split(',')).filter(_.nonEmpty)
    val (invalid, valid) = l.partition(_.contains(":"))
    if (invalid.nonEmpty) {
      Console.err.println(s"Invalid target IDs:")
      for (t <- invalid)
        Console.err.println(s"  $t")
      sys.exit(255)
    }
    if (valid.isEmpty && shared.nonEmpty)
      Array("default")
    else
      valid.toArray
  }

  private lazy val (validIsolated, unrecognizedIsolated) = shared.partition(s => targetsOrExit.exists(t => s.startsWith(t + ":")))

  def checkOrExit(): Unit =
    if (unrecognizedIsolated.nonEmpty) {
      Console.err.println(s"Unrecognized isolation targets in:")
      for (i <- unrecognizedIsolated)
        Console.err.println(s"  $i")
      sys.exit(255)
    }

  lazy val rawIsolatedOrExit = validIsolated.map { s =>
    val Array(target, dep) = s.split(":", 2)
    target -> dep
  }

  private def isolatedModuleVersions(defaultScalaVersion: String) = rawIsolatedOrExit.groupBy { case (t, _) => t }.map {
    case (t, l) =>
      DependencyParser.moduleVersions(l.map(_._2), defaultScalaVersion).either match {
        case Left(errors0) =>
          errors0.foreach(System.err.println)
          sys.exit(255)
        case Right(elems) =>
          t -> elems
      }
  }

  def isolatedDepsOrExit(defaultScalaVersion: String) =
    isolatedModuleVersions(defaultScalaVersion).map {
      case (t, l) =>
        t -> l.map {
          case (mod, ver) =>
            Dependency(
              mod,
              ver,
              configuration = Configuration.runtime,
              attributes = Attributes()
            )
        }
    }

}

object SharedLoaderOptions {
  implicit val parser = Parser[SharedLoaderOptions]
  implicit val help = caseapp.core.help.Help[SharedLoaderOptions]
}
