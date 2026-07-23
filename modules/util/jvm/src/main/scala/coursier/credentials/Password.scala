package coursier.credentials

import dataclass.data


@data case class Password[T](value: T) {
  override def toString(): String = "****"
  override def hashCode(): Int    = "****".##
}
