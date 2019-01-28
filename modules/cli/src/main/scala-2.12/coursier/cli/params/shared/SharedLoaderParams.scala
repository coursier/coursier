package coursier.cli.params.shared

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.options.shared.SharedLoaderOptions
import coursier.cli.resolve.Dependencies
import coursier.core.{Configuration, Dependency}

final case class SharedLoaderParams(
  loaderNames: Seq[String],
  loaderDependencies: Map[String, Seq[Dependency]]
) {
  def allDependencies: Seq[Dependency] =
    loaderNames.flatMap(n => loaderDependencies.getOrElse(n, Nil))
}

object SharedLoaderParams {
  def apply(
    options: SharedLoaderOptions,
    scalaVersion: String,
    defaultConfiguration: Configuration
  ): ValidatedNel[String, SharedLoaderParams] = {

    val targetsOpt = {
      val l = options
        .sharedTarget
        .flatMap(_.split(','))
        .flatMap(_.split(':'))
        .filter(_.nonEmpty)
      Some(l).filter(_.nonEmpty)
    }

    val depsV = options.shared
      .traverse { d =>
        d.split(":", 2) match {
          case Array(target, dep) =>
            Dependencies.parseSimpleDependency(dep, scalaVersion, defaultConfiguration) match {
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

    depsV.map { deps =>
      SharedLoaderParams(
        targetsOpt.getOrElse(deps.map(_._1).distinct),
        deps.groupBy(_._1).mapValues(_.map(_._2)).iterator.toMap
      )
    }
  }
}
