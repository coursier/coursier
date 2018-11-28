package coursier.util

import coursier.core._
import coursier.util.Parse.ModuleRequirements

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

  implicit class SafeDependency(val sc: StringContext) extends AnyVal {
    def dep(args: Any*): Dependency = macro safeDependency
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
        Parse.module(modString, scala.util.Properties.versionNumberString) match {
          case Left(e) =>
            c.abort(c.enclosingPosition, s"Error parsing module $modString: $e")
          case Right(mod) =>
            val attrs = mod.attributes.toSeq.map {
              case (k, v) =>
                q"_root_.scala.Tuple2($k, $v)"
            }
            c.Expr( q"""
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

  private val safeDefModuleRequirements = ModuleRequirements(
    defaultConfiguration = Configuration.empty // same as coursier.Dependency.apply default value for configuration
  )
  def safeDependency(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Dependency] = {
    import c.universe._
    c.prefix.tree match {
      case Apply(_, List(Apply(_, Literal(Constant(modString: String)) :: Nil))) =>
        Parse.moduleVersionConfig(modString, safeDefModuleRequirements, transitive = true, scala.util.Properties.versionNumberString) match {
          case Left(e) =>
            c.abort(c.enclosingPosition, s"Error parsing module $modString: $e")
          case Right((dep, config)) =>
            assert(config.isEmpty)
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
}
