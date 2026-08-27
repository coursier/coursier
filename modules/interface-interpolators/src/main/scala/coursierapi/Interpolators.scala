package coursierapi

import scala.collection.JavaConverters._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object Interpolators {

  final class Macros(val c: blackbox.Context) {
    import c.universe._

    private def unsafeGetPrefixString: String = {
      c.prefix.tree match {
        case Apply(_, List(Apply(_, Literal(Constant(string: String)) :: Nil))) => string
        case _  => c.abort(c.enclosingPosition, "Only a single String literal is allowed here")
      }
    }

    private def scalaVersion = ScalaVersion.of(scala.util.Properties.versionNumberString)

    private def toModuleExpr(mod: Module): Expr[Module] = {
      val attrs = mod.getAttributes.asScala.toSeq.map { case (k, v) => q"_root_.scala.Tuple2($k, $v)" }
      c.Expr(q"""
        _root_.coursierapi.Module.of(
          ${mod.getOrganization},
          ${mod.getName},
          _root_.scala.collection.JavaConverters.mapAsJavaMapConverter(_root_.scala.collection.immutable.Map(..$attrs)).asJava
        )
      """)
    }

    def safeModule(args: Expr[Any]*): Expr[Module] = {
      val modString = unsafeGetPrefixString
      val mod = Module.parse(modString, scalaVersion)
      toModuleExpr(mod)
    }

    def safeDependency(args: Expr[Any]*): Expr[Dependency] = {
      val depString = unsafeGetPrefixString
      val dep = Dependency.parse(depString, scalaVersion)
      c.Expr(q"_root_.coursierapi.Dependency.of(${toModuleExpr(dep.getModule)}, ${dep.getVersion})")
    }

    def safeMavenRepository(args: Expr[Any]*): Expr[MavenRepository] = {
      val root = unsafeGetPrefixString
      // FIXME Check that there's no query string, fragment, … in uri?
      val uri = new java.net.URI(root)
      c.Expr(q"""_root_.coursierapi.MavenRepository.of($root)""")
    }

    def safeIvyRepository(args: Expr[Any]*): Expr[IvyRepository] = {
      val str = unsafeGetPrefixString
      // FIXME Check that there's no query string, fragment, … in uri?
      val r = IvyRepository.of(str)
      // Here, ideally, we should lift r as an Expr, but this is quite cumbersome to do (it involves lifting
      // Seq[coursier.ivy.Pattern.Chunk], where coursier.ivy.Pattern.Chunk is an ADT, …
      c.Expr(q"""_root_.coursierapi.IvyRepository.of($str)""")
    }
  }

  implicit class moduleString(val sc: StringContext) extends AnyVal {
    def mod(args: Any*): Module = macro Macros.safeModule
  }

  implicit class dependencyString(val sc: StringContext) extends AnyVal {
    def dep(args: Any*): Dependency = macro Macros.safeDependency
  }

  implicit class mavenRepositoryString(val sc: StringContext) extends AnyVal {
    def mvn(args: Any*): MavenRepository = macro Macros.safeMavenRepository
  }

  implicit class ivyRepositoryString(val sc: StringContext) extends AnyVal {
    def ivy(args: Any*): IvyRepository = macro Macros.safeIvyRepository
  }

}
