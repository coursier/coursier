package coursier.util

import coursier.core._
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.parse.{DependencyParser, ModuleParser}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object StringInterpolators {

  // adapted from https://github.com/criteo/cuttle/blob/7e04a8f93c2c44992da270242d8b94ea808ac623/timeseries/src/main/scala/com/criteo/cuttle/timeseries/package.scala#L39-L56

  implicit class SafeOrganization(val sc: StringContext) extends AnyVal {
    def org(args: Any*): Organization = macro safeOrganization
  }

  implicit class SafeModuleName(val sc: StringContext) extends AnyVal {
    def name(args: Any*): ModuleName = macro safeModuleName
  }

  implicit class SafeModule(val sc: StringContext) extends AnyVal {
    def mod(args: Any*): Module = macro safeModule
  }

  implicit class SafeModuleExclusionMatcher(val sc: StringContext) extends AnyVal {
    def excl(args: Any*): ModuleMatchers = macro safeModuleExclusionMatcher
  }

  implicit class SafeModuleInclusionMatcher(val sc: StringContext) extends AnyVal {
    def incl(args: Any*): ModuleMatchers = macro safeModuleInclusionMatcher
  }

  implicit class SafeDependency(val sc: StringContext) extends AnyVal {
    def dep(args: Any*): Dependency = macro safeDependency
  }

  implicit class SafeMavenRepository(val sc: StringContext) extends AnyVal {
    def mvn(args: Any*): MavenRepository = macro safeMavenRepository
  }

  implicit class SafeIvyRepository(val sc: StringContext) extends AnyVal {
    def ivy(args: Any*): IvyRepository = macro safeIvyRepository
  }

  def safeOrganization(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Organization] = {
    import c.universe._
    c.prefix.tree match {
      case Apply(_, List(Apply(_, Literal(Constant(orgString: String)) :: Nil))) =>
        // TODO Check for invalid characters
        c.Expr(q"""_root_.coursier.core.Organization($orgString)""")
      case _ =>
        c.abort(c.enclosingPosition, s"Only a single String literal is allowed here")
    }
  }

  def safeModuleName(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[ModuleName] = {
    import c.universe._
    c.prefix.tree match {
      case Apply(_, List(Apply(_, Literal(Constant(nameString: String)) :: Nil))) =>
        // TODO Check for invalid characters
        c.Expr(q"""_root_.coursier.core.ModuleName($nameString)""")
      case _ =>
        c.abort(c.enclosingPosition, s"Only a single String literal is allowed here")
    }
  }

  def safeModule(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Module] = {
    import c.universe._
    c.prefix.tree match {
      case Apply(_, List(Apply(_, Literal(Constant(modString: String)) :: Nil))) =>
        ModuleParser.module(modString, scala.util.Properties.versionNumberString) match {
          case Left(e) =>
            c.abort(c.enclosingPosition, s"Error parsing module $modString: $e")
          case Right(mod) =>
            val attrs = mod.attributes.toSeq.map {
              case (k, v) =>
                q"_root_.scala.Tuple2($k, $v)"
            }
            c.Expr(q"""
              _root_.coursier.core.Module(
                _root_.coursier.core.Organization(${mod.organization.value}),
                _root_.coursier.core.ModuleName(${mod.name.value}),
                _root_.scala.collection.immutable.Map(..$attrs)
              )
            """)
        }
      case _ =>
        c.abort(c.enclosingPosition, s"Only a single String literal is allowed here")
    }
  }

  def safeModuleExclusionMatcher(
    c: blackbox.Context
  )(
    args: c.Expr[Any]*
  ): c.Expr[ModuleMatchers] = {
    import c.universe._
    c.Expr(q"""
      _root_.coursier.util.ModuleMatchers(
        _root_.scala.collection.immutable.Set[_root_.coursier.util.ModuleMatcher](
          _root_.coursier.util.ModuleMatcher(${safeModule(c)(args: _*)})
        )
      )
    """)
  }

  def safeModuleInclusionMatcher(
    c: blackbox.Context
  )(
    args: c.Expr[Any]*
  ): c.Expr[ModuleMatchers] = {
    import c.universe._
    c.Expr(q"""
      _root_.coursier.util.ModuleMatchers(
        _root_.scala.collection.immutable.Set.empty[_root_.coursier.util.ModuleMatcher],
        _root_.scala.collection.immutable.Set[_root_.coursier.util.ModuleMatcher](
          _root_.coursier.util.ModuleMatcher(${safeModule(c)(args: _*)})
        )
      )
    """)
  }

  private def matcherTree(c: blackbox.Context)(matcher: VariantSelector.VariantMatcher)
    : c.Expr[VariantSelector.VariantMatcher] = {
    import c.universe._
    matcher match {
      case VariantSelector.VariantMatcher.Api =>
        c.Expr(q"_root_.coursier.core.VariantSelector.VariantMatcher.Api")
      case VariantSelector.VariantMatcher.Runtime =>
        c.Expr(q"_root_.coursier.core.VariantSelector.VariantMatcher.Runtime")
      case eq: VariantSelector.VariantMatcher.Equals =>
        c.Expr(q"_root_.coursier.core.VariantSelector.VariantMatcher.Equals(${eq.value})")
      case mv: VariantSelector.VariantMatcher.MinimumVersion =>
        c.Expr(
          q"_root_.coursier.core.VariantSelector.VariantMatcher.MinimumVersion(_root_.coursier.version.Version(${mv.minimumVersion.asString}))"
        )
      case ew: VariantSelector.VariantMatcher.EndsWith =>
        c.Expr(q"_root_.coursier.core.VariantSelector.VariantMatcher.EndsWith(${ew.suffix})")
      case anyOf: VariantSelector.VariantMatcher.AnyOf =>
        val values = anyOf.matchers.map(matcherTree(c)(_))
        c.Expr(
          q"_root_.coursier.core.VariantSelector.VariantMatcher.AnyOf(_root_.scala.collection.immutable.Seq(..$values))"
        )
    }
  }

  def safeDependency(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Dependency] = {
    import c.universe._
    c.prefix.tree match {
      case Apply(_, List(Apply(_, Literal(Constant(modString: String)) :: Nil))) =>
        // same default configuration as coursier.Dependency.apply
        DependencyParser.dependency(
          modString,
          scala.util.Properties.versionNumberString,
          Configuration.empty
        ) match {
          case Left(e) =>
            c.abort(c.enclosingPosition, s"Error parsing module $modString: $e")
          case Right(dep) =>
            val attrs = dep.module.attributes.toSeq.map {
              case (k, v) =>
                q"_root_.scala.Tuple2($k, $v)"
            }
            val excls = dep.minimizedExclusions.toSeq().map {
              case (org, name) =>
                q"_root_.scala.Tuple2(_root_.coursier.core.Organization(${org.value}), _root_.coursier.core.ModuleName(${name.value}))"
            }
            val overrides = dep.overridesMap.flatten.toSeq.sortBy(_._1.repr).map {
              case (key, values) =>
                val key0 = q"""_root_.coursier.core.DependencyManagement.Key(
                  _root_.coursier.core.Organization(${key.organization.value}),
                  _root_.coursier.core.ModuleName(${key.name.value}),
                  _root_.coursier.core.Type(${key.`type`.value}),
                  _root_.coursier.core.Classifier(${key.classifier.value})
                )"""
                val excls = values.minimizedExclusions.toSeq().map {
                  case (org, name) =>
                    q"_root_.scala.Tuple2(_root_.coursier.core.Organization(${org.value}), _root_.coursier.core.ModuleName(${name.value}))"
                }
                val values0 = q"""_root_.coursier.core.DependencyManagement.Values(
                  _root_.coursier.core.Configuration(${values.config.value}),
                  _root_.coursier.version.VersionConstraint( // FIXME could be parsed eagerly at compile-time
                    ${values.versionConstraint.asString}
                  ),
                  _root_.coursier.core.MinimizedExclusions(_root_.scala.collection.immutable.Set[(_root_.coursier.core.Organization, _root_.coursier.core.ModuleName)](..$excls)),
                  ${values.optional}
                )"""
                q"_root_.scala.Tuple2($key0, $values0)"
            }
            val boms = dep.bomDependencies.map { bomDep =>
              val attrs = bomDep.module.attributes.toSeq.map {
                case (k, v) =>
                  q"_root_.scala.Tuple2($k, $v)"
              }
              q"""_root_.coursier.core.BomDependency(
                _root_.coursier.core.Module(
                  _root_.coursier.core.Organization(${bomDep.module.organization.value}),
                  _root_.coursier.core.ModuleName(${bomDep.module.name.value}),
                  _root_.scala.collection.immutable.Map(..$attrs)
                ),
                _root_.coursier.version.VersionConstraint( // FIXME could be parsed eagerly at compile-time
                  ${bomDep.versionConstraint.asString}
                ),
                _root_.coursier.core.Configuration(${bomDep.config.value}),
                ${bomDep.forceOverrideVersions}
              )"""
            }
            val variantSelector = dep.variantSelector match {
              case c: VariantSelector.ConfigurationBased =>
                q"""_root_.coursier.core.VariantSelector.ConfigurationBased(
                  _root_.coursier.core.Configuration(${c.configuration.value})
                )"""
              case a: VariantSelector.AttributesBased =>
                val entries = a.matchers.toVector.sortBy(_._1).map {
                  case (k, v) =>
                    q"_root_.scala.Tuple2($k, ${matcherTree(c)(v)})"
                }
                q"""_root_.coursier.core.VariantSelector.AttributesBased(
                  _root_.scala.collection.immutable.Map[_root_.java.lang.String, _root_.coursier.core.VariantSelector.VariantMatcher](..$entries)
                )"""
            }
            c.Expr(q"""
              _root_.coursier.core.Dependency(
                _root_.coursier.core.Module(
                  _root_.coursier.core.Organization(${dep.module.organization.value}),
                  _root_.coursier.core.ModuleName(${dep.module.name.value}),
                  _root_.scala.collection.immutable.Map(..$attrs)
                ),
                _root_.coursier.version.VersionConstraint(
                  ${dep.versionConstraint.asString}
                ),
                $variantSelector,
                _root_.coursier.core.MinimizedExclusions(_root_.scala.collection.immutable.Set[(_root_.coursier.core.Organization, _root_.coursier.core.ModuleName)](..$excls)),
                _root_.coursier.core.Publication(
                  ${dep.publication.name},
                  _root_.coursier.core.Type(${dep.publication.`type`.value}),
                  _root_.coursier.core.Extension(${dep.publication.ext.value}),
                  _root_.coursier.core.Classifier(${dep.publication.classifier.value})
                ),
                ${dep.optional},
                ${dep.transitive},
                _root_.scala.collection.immutable.Map[_root_.coursier.core.DependencyManagement.Key, _root_.coursier.core.DependencyManagement.Values](),
                _root_.scala.collection.immutable.Nil,
                _root_.scala.collection.immutable.Seq(..$boms),
                _root_.coursier.core.Overrides(
                  _root_.scala.collection.immutable.Map[_root_.coursier.core.DependencyManagement.Key, _root_.coursier.core.DependencyManagement.Values](..$overrides)
                ),
                ${dep.endorseStrictVersions}
              )
            """)
        }
      case _ =>
        c.abort(c.enclosingPosition, s"Only a single String literal is allowed here")
    }
  }

  def safeMavenRepository(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[MavenRepository] = {
    import c.universe._
    c.prefix.tree match {
      case Apply(_, List(Apply(_, Literal(Constant(root: String)) :: Nil))) =>
        // FIXME Check that there's no query string, fragment, … in uri?
        new java.net.URI(root)
        c.Expr(q"""_root_.coursier.maven.MavenRepository($root)""")
      case _ =>
        c.abort(c.enclosingPosition, s"Only a single String literal is allowed here")
    }
  }

  def safeIvyRepository(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[IvyRepository] = {
    import c.universe._
    c.prefix.tree match {
      case Apply(_, List(Apply(_, Literal(Constant(str: String)) :: Nil))) =>
        // FIXME Check that there's no query string, fragment, … in uri?
        IvyRepository.parse(str) match {
          case Left(e) =>
            c.abort(c.enclosingPosition, s"Malformed Ivy repository '$str': $e")
          case Right(r0) =>
        }
        // Here, ideally, we should lift r as an Expr, but this is quite cumbersome to do (it involves lifting
        // Seq[coursier.ivy.Pattern.Chunk], where coursier.ivy.Pattern.Chunk is an ADT, …
        c.Expr(
          q"""_root_.coursier.ivy.IvyRepository.parse($str).left.map(e => sys.error("Error parsing Ivy repository: " + e)).merge"""
        )
      case _ =>
        c.abort(c.enclosingPosition, s"Only a single String literal is allowed here")
    }
  }
}
