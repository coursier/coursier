package coursier.credentials
import dataclass.data

@data class Password[T](value: T) {
  override def toString(): String = "****"
  override def hashCode(): Int = "****".##
}
