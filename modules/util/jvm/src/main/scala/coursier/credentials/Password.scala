package coursier.credentials

final case class Password[T](value: T) {
  override def toString(): String = "****"
  override def hashCode(): Int    = "****".##
}
