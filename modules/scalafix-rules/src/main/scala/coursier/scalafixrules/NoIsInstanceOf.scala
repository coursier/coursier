package coursier.scalafixrules

import scala.meta._

import scalafix.v1._

/** Bans `isInstanceOf`.
  *
  * A type test tells us what a value is, then throws that knowledge away: the cast that inevitably
  * follows it is unchecked, and nothing ties the two together if one of them is later edited.
  * Pattern matching on the type keeps the test and the refined type in one place, and lets the
  * compiler check exhaustivity of sealed hierarchies.
  */
class NoIsInstanceOf extends SyntacticRule("NoIsInstanceOf") {

  override def description: String =
    "Bans isInstanceOf, pattern match on the type instead"

  override def fix(implicit doc: SyntacticDocument): Patch =
    doc.tree.collect {
      case tree @ Term.Name("isInstanceOf") =>
        Patch.lint(NoIsInstanceOf.IsInstanceOfDiagnostic(tree))
    }.asPatch
}

object NoIsInstanceOf {
  private final case class IsInstanceOfDiagnostic(tree: Tree) extends Diagnostic {
    def position: Position = tree.pos
    def message: String =
      "isInstanceOf is not allowed, pattern match on the type instead " +
        "(x match { case _: Foo => true; case _ => false })"
    override def categoryID: String = "isInstanceOf"
  }
}
