package coursier.util

import coursier.core.Organization

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object StringInterpolators {

  // adapted from https://github.com/criteo/cuttle/blob/7e04a8f93c2c44992da270242d8b94ea808ac623/timeseries/src/main/scala/com/criteo/cuttle/timeseries/package.scala#L39-L56

  implicit class SafeOrganization(val sc: StringContext) extends AnyVal {
    def org(args: Any*): Organization = macro safeOrganization
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
}
