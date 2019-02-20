package coursier.params.rule

sealed abstract class RuleResolution extends Product with Serializable {
  def isWarn: Boolean =
    this match {
      case RuleResolution.Warn => true
      case _ => false
    }
}

object RuleResolution {
  case object TryResolve extends RuleResolution
  case object Warn extends RuleResolution
  case object Fail extends RuleResolution
}
