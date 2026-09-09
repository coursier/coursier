package coursier.util

import coursier.core._
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.parse.{DependencyParser, ModuleParser}
import coursier.version.{Version => Version0, VersionConstraint => VersionConstraint0}

import scala.quoted.{Expr, Exprs, Quotes, Varargs, quotes}

/** Implementations of the macros behind the interpolators of [[StringInterpolators]].
  *
  * These mirror the Scala 2 macros of `../scala-2/coursier/util/StringInterpolators.scala`: the
  * literal is parsed at compile-time, and the interpolation expands to the value it was parsed to.
  *
  * They live in their own file as macros cannot be expanded in the compilation unit that defines
  * them.
  *
  * Beware that the expressions below build collections element by element, rather than with the
  * more straightforward `Map(…)` / `Set(…)` / `Seq(…)`: the vararg `SeqLiteral`s the latter leave
  * behind once inlined make dotty-cps-async fail, and these interpolators are used from `async`
  * blocks in the tests.
  */
object StringInterpolatorsMacros {

  private def single(sc: Expr[StringContext])(using Quotes): String =
    sc match {
      case '{ StringContext(${ Varargs(Exprs(parts)) }*) } if parts.length == 1 =>
        parts.head
      case '{ new StringContext(${ Varargs(Exprs(parts)) }*) } if parts.length == 1 =>
        parts.head
      case _ =>
        quotes.reflect.report.errorAndAbort("Only a single String literal is allowed here", sc)
    }

  // Same as what the Scala 2 macros use, that is the Scala version things are compiled with
  private def scalaVersion: String =
    scala.util.Properties.versionNumberString

  private def attributesExpr(
    attributes: Map[String, String]
  )(using Quotes): Expr[Map[String, String]] =
    attributes.toSeq.sortBy(_._1).foldLeft('{ Map.empty[String, String] }) {
      case (acc, (k, v)) =>
        '{ $acc.updated(${ Expr(k) }, ${ Expr(v) }) }
    }

  private def moduleExpr(module: Module)(using Quotes): Expr[Module] =
    '{
      Module(
        Organization(${ Expr(module.organization.value) }),
        ModuleName(${ Expr(module.name.value) }),
        ${ attributesExpr(module.attributes) }
      )
    }

  private def exclusionsExpr(
    exclusions: Seq[(Organization, ModuleName)]
  )(using Quotes): Expr[MinimizedExclusions] = {
    val set = exclusions
      .sortBy {
        case (org, name) =>
          (org.value, name.value)
      }
      .foldLeft('{ Set.empty[(Organization, ModuleName)] }) {
        case (acc, (org, name)) =>
          '{ $acc + ((Organization(${ Expr(org.value) }), ModuleName(${ Expr(name.value) }))) }
      }
    '{ MinimizedExclusions($set) }
  }

  private def matcherExpr(
    matcher: VariantSelector.VariantMatcher
  )(using Quotes): Expr[VariantSelector.VariantMatcher] =
    matcher match {
      case VariantSelector.VariantMatcher.Api =>
        '{ VariantSelector.VariantMatcher.Api }
      case VariantSelector.VariantMatcher.Runtime =>
        '{ VariantSelector.VariantMatcher.Runtime }
      case equals0: VariantSelector.VariantMatcher.Equals =>
        '{ VariantSelector.VariantMatcher.Equals(${ Expr(equals0.value) }) }
      case minVersion: VariantSelector.VariantMatcher.MinimumVersion =>
        '{
          VariantSelector.VariantMatcher.MinimumVersion(
            Version0(${ Expr(minVersion.minimumVersion.asString) })
          )
        }
      case endsWith: VariantSelector.VariantMatcher.EndsWith =>
        '{ VariantSelector.VariantMatcher.EndsWith(${ Expr(endsWith.suffix) }) }
      case anyOf: VariantSelector.VariantMatcher.AnyOf =>
        val matchers =
          anyOf.matchers.foldLeft('{ Seq.empty[VariantSelector.VariantMatcher] }) { (acc, m) =>
            '{ $acc :+ ${ matcherExpr(m) } }
          }
        '{ VariantSelector.VariantMatcher.AnyOf($matchers) }
    }

  private def variantSelectorExpr(
    variantSelector: VariantSelector
  )(using Quotes): Expr[VariantSelector] =
    variantSelector match {
      case configBased: VariantSelector.ConfigurationBased =>
        '{
          VariantSelector.ConfigurationBased(
            Configuration(${ Expr(configBased.configuration.value) })
          )
        }
      case attributesBased: VariantSelector.AttributesBased =>
        val matchers = attributesBased
          .matchers
          .toVector
          .sortBy(_._1)
          .foldLeft('{ Map.empty[String, VariantSelector.VariantMatcher] }) {
            case (acc, (k, matcher)) =>
              '{ $acc.updated(${ Expr(k) }, ${ matcherExpr(matcher) }) }
          }
        '{ VariantSelector.AttributesBased($matchers) }
    }

  private def parseModule(sc: Expr[StringContext])(using Quotes): Module = {
    val input = single(sc)
    ModuleParser.module(input, scalaVersion) match {
      case Left(e) =>
        quotes.reflect.report.errorAndAbort(s"Error parsing module $input: $e", sc)
      case Right(module) =>
        module
    }
  }

  def orgImpl(sc: Expr[StringContext])(using Quotes): Expr[Organization] = {
    // TODO Check for invalid characters
    val input = single(sc)
    '{ Organization(${ Expr(input) }) }
  }

  def nameImpl(sc: Expr[StringContext])(using Quotes): Expr[ModuleName] = {
    // TODO Check for invalid characters
    val input = single(sc)
    '{ ModuleName(${ Expr(input) }) }
  }

  def modImpl(sc: Expr[StringContext])(using Quotes): Expr[Module] =
    moduleExpr(parseModule(sc))

  def exclImpl(sc: Expr[StringContext])(using Quotes): Expr[ModuleMatchers] = {
    val module = parseModule(sc)
    '{ ModuleMatchers(Set.empty[ModuleMatcher] + ModuleMatcher(${ moduleExpr(module) })) }
  }

  def inclImpl(sc: Expr[StringContext])(using Quotes): Expr[ModuleMatchers] = {
    val module = parseModule(sc)
    '{
      ModuleMatchers(
        Set.empty[ModuleMatcher],
        Set.empty[ModuleMatcher] + ModuleMatcher(${ moduleExpr(module) })
      )
    }
  }

  def depImpl(sc: Expr[StringContext])(using Quotes): Expr[Dependency] = {
    val input = single(sc)
    // same default configuration as coursier.Dependency.apply
    DependencyParser.dependency(input, scalaVersion, Configuration.empty) match {
      case Left(e) =>
        quotes.reflect.report.errorAndAbort(s"Error parsing dependency $input: $e", sc)
      case Right(dep) =>
        val boms = dep.bomDependencies.foldLeft('{ Seq.empty[BomDependency] }) { (acc, bomDep) =>
          '{
            $acc :+ BomDependency(
              ${ moduleExpr(bomDep.module) },
              // FIXME could be parsed eagerly at compile-time
              VersionConstraint0(${ Expr(bomDep.versionConstraint.asString) }),
              Configuration(${ Expr(bomDep.config.value) }),
              ${ Expr(bomDep.forceOverrideVersions) }
            )
          }
        }
        val overrides = dep
          .overridesMap
          .flatten
          .toSeq
          .sortBy(_._1.repr)
          .foldLeft('{ Map.empty[DependencyManagement.Key, DependencyManagement.Values] }) {
            case (acc, (key, values)) =>
              '{
                $acc.updated(
                  DependencyManagement.Key(
                    Organization(${ Expr(key.organization.value) }),
                    ModuleName(${ Expr(key.name.value) }),
                    coursier.core.Type(${ Expr(key.`type`.value) }),
                    Classifier(${ Expr(key.classifier.value) })
                  ),
                  DependencyManagement.Values(
                    Configuration(${ Expr(values.config.value) }),
                    // FIXME could be parsed eagerly at compile-time
                    VersionConstraint0(${ Expr(values.versionConstraint.asString) }),
                    ${ exclusionsExpr(values.minimizedExclusions.toSeq()) },
                    ${ Expr(values.optional0) }
                  )
                )
              }
          }
        '{
          Dependency(
            ${ moduleExpr(dep.module) },
            VersionConstraint0(${ Expr(dep.versionConstraint.asString) }),
            ${ variantSelectorExpr(dep.variantSelector) },
            ${ exclusionsExpr(dep.minimizedExclusions.toSeq()) },
            Publication(
              ${ Expr(dep.publication.name) },
              coursier.core.Type(${ Expr(dep.publication.`type`.value) }),
              Extension(${ Expr(dep.publication.ext.value) }),
              Classifier(${ Expr(dep.publication.classifier.value) })
            ),
            ${ Expr(dep.optional0) },
            ${ Expr(dep.transitive) },
            Map.empty[DependencyManagement.Key, DependencyManagement.Values],
            Nil,
            $boms,
            Overrides($overrides),
            ${ Expr(dep.endorseStrictVersions) }
          )
        }
    }
  }

  def mvnImpl(sc: Expr[StringContext])(using Quotes): Expr[MavenRepository] = {
    val input = single(sc)
    // FIXME Check that there's no query string, fragment, … in uri?
    new java.net.URI(input)
    '{ MavenRepository(${ Expr(input) }) }
  }

  def ivyImpl(sc: Expr[StringContext])(using Quotes): Expr[IvyRepository] = {
    val input = single(sc)
    // FIXME Check that there's no query string, fragment, … in uri?
    IvyRepository.parse(input) match {
      case Left(e) =>
        quotes.reflect.report.errorAndAbort(s"Malformed Ivy repository '$input': $e", sc)
      case Right(_) =>
    }
    // Here, ideally, we should lift the parsed repository as an Expr, but this is quite cumbersome
    // to do (it involves lifting Seq[coursier.ivy.Pattern.Chunk], where coursier.ivy.Pattern.Chunk
    // is an ADT, …)
    '{
      IvyRepository.parse(${ Expr(input) }) match {
        case Left(e)  => sys.error("Error parsing Ivy repository: " + e)
        case Right(r) => r
      }
    }
  }
}
