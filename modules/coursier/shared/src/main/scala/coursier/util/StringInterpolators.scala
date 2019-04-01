package coursier.util

import coursier.core._
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.parse.DependencyParser

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
        coursier.util.Parse.module(modString, scala.util.Properties.versionNumberString) match {
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

  def safeModuleExclusionMatcher(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[ModuleMatchers] = {
    import c.universe._
    c.Expr(q"""
      _root_.coursier.util.ModuleMatchers(
        _root_.scala.collection.immutable.Set[_root_.coursier.util.ModuleMatcher](
          _root_.coursier.util.ModuleMatcher(${safeModule(c)(args: _*)})
        )
      )
    """)
  }

  def safeModuleInclusionMatcher(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[ModuleMatchers] = {
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

  def safeDependency(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Dependency] = {
    import c.universe._
    c.prefix.tree match {
      case Apply(_, List(Apply(_, Literal(Constant(modString: String)) :: Nil))) =>
        // same default configuration as coursier.Dependency.apply
        DependencyParser.dependency(modString, scala.util.Properties.versionNumberString, Configuration.empty) match {
          case Left(e) =>
            c.abort(c.enclosingPosition, s"Error parsing module $modString: $e")
          case Right(dep) =>
            val attrs = dep.module.attributes.toSeq.map {
              case (k, v) =>
                q"_root_.scala.Tuple2($k, $v)"
            }
            val excls = dep.exclusions.toSeq.map {
              case (org, name) =>
                q"_root_.scala.Tuple2(_root_.coursier.core.Organization(${org.value}), _root_.coursier.core.ModuleName(${name.value}))"
            }
            c.Expr(q"""
              _root_.coursier.core.Dependency(
                _root_.coursier.core.Module(
                  _root_.coursier.core.Organization(${dep.module.organization.value}),
                  _root_.coursier.core.ModuleName(${dep.module.name.value}),
                  _root_.scala.collection.immutable.Map(..$attrs)
                ),
                ${dep.version},
                _root_.coursier.core.Configuration(${dep.configuration.value}),
                _root_.scala.collection.immutable.Set(..$excls),
                _root_.coursier.core.Attributes(
                  _root_.coursier.core.Type(${dep.attributes.`type`.value}),
                  _root_.coursier.core.Classifier(${dep.attributes.classifier.value})
                ),
                ${dep.optional},
                ${dep.transitive}
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
        val uri = new java.net.URI(root)
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
        val r = IvyRepository.parse(str) match {
          case Left(e) =>
            c.abort(c.enclosingPosition, s"Malformed Ivy repository '$str': $e")
          case Right(r0) => r0
        }
        // Here, ideally, we should lift r as an Expr, but this is quite cumbersome to do (it involves lifting
        // Seq[coursier.ivy.Pattern.Chunk], where coursier.ivy.Pattern.Chunk is an ADT, …
        c.Expr(q"""_root_.coursier.ivy.IvyRepository.parse($str).left.map(e => sys.error("Error parsing Ivy repository: " + e)).merge""")
      case _ =>
        c.abort(c.enclosingPosition, s"Only a single String literal is allowed here")
    }
  }
}
