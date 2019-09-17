package coursier.cli.params

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.dependencyString
import coursier.cli.options.SharedLoaderOptions
import coursier.parse.{DependencyParser, JavaOrScalaDependency, ModuleParser}

final case class SharedLoaderParams(
  loaderNames: Seq[String],
  loaderDependencies: Map[String, Seq[JavaOrScalaDependency]]
)

object SharedLoaderParams {
  def from(options: SharedLoaderOptions): ValidatedNel[String, SharedLoaderParams] = {

    val targetsOpt = {
      val l = options
        .sharedTarget
        .flatMap(_.split(','))
        .flatMap(_.split(':'))
        .filter(_.nonEmpty)
      Some(l).filter(_.nonEmpty)
    }

    val defaultTarget = targetsOpt
      .flatMap(_.headOption)
      .getOrElse("default")

    val depsFromDeprecatedArgsV = options
      .isolated
      .traverse { d =>
        d.split(":", 2) match {
          case Array(target, dep) =>
            DependencyParser.javaOrScalaDependencyParams(dep) match {
              case Left(err) =>
                Validated.invalidNel(s"$d: $err")
              case Right((dep0, params)) =>
                if (params.isEmpty)
                  Validated.validNel(target -> dep0)
                else
                  Validated.invalidNel(s"$d: extra dependency parameters not supported for shared loader dependencies")
            }
          case _ =>
            Validated.invalidNel(s"$d: malformed shared dependency (expected target:org:name:version)")
        }
      }

    val depsV = options
      .shared
      .traverse { d =>

        val (target, dep) = d.split("@", 2) match {
          case Array(dep0, target0) =>
            (target0, dep0)
          case Array(dep0) =>
            (defaultTarget, dep0)
        }

        ModuleParser.javaOrScalaModule(dep) match {
          case Left(err) =>
            Validated.invalidNel(s"$d: $err")
          case Right(m) =>
            val asDep = JavaOrScalaDependency(m, dep"_:_:_") // actual version shouldn't matter
            Validated.validNel(target -> asDep)
        }
      }

    (depsFromDeprecatedArgsV, depsV).mapN {
      (depsFromDeprecatedArgs, deps) =>
        val deps0 = depsFromDeprecatedArgs ++ deps
        SharedLoaderParams(
          targetsOpt.getOrElse(deps0.map(_._1).distinct),
          deps0.groupBy(_._1).mapValues(_.map(_._2)).iterator.toMap
        )
    }
  }
}
