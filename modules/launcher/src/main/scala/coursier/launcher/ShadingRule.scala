package coursier.launcher

import dataclass.data

/** A class relocation rule, applied when generating an assembly.
  *
  * These follow the same semantics as the rules of
  * [[https://github.com/coursier/sbt-shading sbt-shading]], and are ultimately translated to
  * [[https://github.com/eed3si9n/jarjar-abrams jarjar-abrams]] rules.
  */
sealed abstract class ShadingRule extends Product with Serializable {

  /** The jarjar pattern this rule matches (e.g. `foo.**`). */
  def pattern: String

  /** The jarjar result this rule renames matches to (e.g. `shaded.foo.@1`). */
  def result: String
}

object ShadingRule {

  /** Moves everything under package `from` to be under `to.from`.
    *
    * For instance `moveUnder("foo", "shaded")` relocates `foo.Bar` to `shaded.foo.Bar`. This
    * matches the `moveUnder` helper of sbt-shading.
    */
  @data case class MoveUnder(from: String, to: String) extends ShadingRule {
    def pattern: String = s"$from.**"
    def result: String  = s"$to.$from.@1"
  }

  /** Renames package `from` to `to`.
    *
    * For instance `rename("foo", "shaded.foo")` relocates `foo.Bar` to `shaded.foo.Bar`.
    */
  @data case class Rename(from: String, to: String) extends ShadingRule {
    def pattern: String = s"$from.**"
    def result: String  = s"$to.@1"
  }

  /** A raw jarjar rule, with its pattern and result. */
  @data case class Patterns(pattern: String, result: String) extends ShadingRule

  def moveUnder(from: String, to: String): ShadingRule =
    MoveUnder(from, to)
  def rename(from: String, to: String): ShadingRule =
    Rename(from, to)
}
