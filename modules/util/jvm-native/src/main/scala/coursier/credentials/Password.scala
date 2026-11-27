package coursier.credentials

import dataclass.data

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class Password[T](value: T) {
  override def toString(): String = "****"
  override def hashCode(): Int    = "****".##
}
